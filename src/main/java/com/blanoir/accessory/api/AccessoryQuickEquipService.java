package com.blanoir.accessory.api;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.attributeload.AccessoryLoad;
import com.blanoir.accessory.events.AccessoryPlaceEvent;
import com.blanoir.accessory.utils.LoreUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class AccessoryQuickEquipService {    //这个可以不用管
    private final Accessory plugin;
    private final NamespacedKey LOCKED;

    private final AccessoryLoad accessoryLoad;

    public AccessoryQuickEquipService(Accessory plugin) {
        this.plugin = plugin;
        this.LOCKED = new NamespacedKey(plugin, "locked");
        this.accessoryLoad = new AccessoryLoad(plugin);
    }

    /** 给 KeyEvent / 右键 调用：尝试把主手物品穿戴到饰品槽 */
    public boolean tryEquipMainHand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return false;

        int slot = findAccessorySlot(hand);
        if (slot == -1) return false;

        // 防止把饰品塞进外框锁定槽（如果你 frame 槽位和 accessory 槽位配置冲突）
        if (isFrameSlot(slot)) return false;

        return equipToSlot(p, slot);
    }

    /** 核心：把主手物品 equip 到指定 slot（会替换旧的） */
    public boolean equipToSlot(Player p, int slot) {
        int size = guiSize();
        if (slot < 0 || slot >= size) return false;

        // 1) 读当前饰品 contents
        ItemStack[] contents = loadContents(p, size);

        ItemStack old = contents[slot];

        // 2) 放入新饰品（只放 1 个）
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return false;

        ItemStack placed = hand.clone();
        placed.setAmount(1);

        AccessoryPlaceEvent placeEvent = new AccessoryPlaceEvent(
                p,
                slot,
                placed.clone(),
                old == null ? null : old.clone()
        );
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            return false;
        }

        contents[slot] = placed;

        // 3) 扣主手 1（稳，不用 e.getItem 引用）
        decrementMainHand(p);

        // 4) 旧饰品回背包 / 满了掉地
        if (old != null && old.getType() != Material.AIR) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(old);
            left.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
        }

        // 5) 保存
        saveContents(p, contents);

        // 6) 刷新属性（AccessoryLoad 扫 inv）
        Inventory tmp = Bukkit.createInventory(null, size);
        tmp.setContents(contents.clone());
        accessoryLoad.rebuildFromInventory(p, tmp);

        // 7) 反馈
        p.sendActionBar(plugin.lang().lang("Accessory_equipped"));

        // 8) （可选）你之后做 HUD 同步，就在这里调用
        // plugin.getHudSync().sync(p, contents);

        return true;
    }

    /** lore -> 找到目标槽位：Accessory.<slot>.lore */
    public int findAccessorySlot(ItemStack item) {
        List<String> lore = LoreUtils.plainLore(item);
        if (lore == null || lore.isEmpty()) return -1;

        var sec = plugin.getConfig().getConfigurationSection("Accessory");
        if (sec == null) return -1;

        for (String key : sec.getKeys(false)) {
            int slot;
            try { slot = Integer.parseInt(key); }
            catch (NumberFormatException ignored) { continue; }

            List<String> need = plugin.getConfig().getStringList("Accessory." + slot + ".lore");
            if (need == null || need.isEmpty()) continue;

            if (LoreUtils.matchesAnyKeyword(lore, need)) return slot;
        }
        return -1;
    }

    // ----------------- storage (和你 InvSave/InvLoad 同一套) -----------------

    private int guiSize() {
        // 你如果 config 里没写 gui.size，就默认 27
        int s = plugin.getConfig().getInt("gui.size", 27);
        // 防御：必须是 9 的倍数
        if (s % 9 != 0) s = 27;
        return s;
    }

    private File dataFile(Player p) {
        File dir = new File(plugin.getDataFolder(), "contains");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, p.getUniqueId() + ".yml");
    }

    private ItemStack[] loadContents(Player p, int size) {
        File f = dataFile(p);
        ItemStack[] arr = new ItemStack[size];

        if (!f.exists()) return arr;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        List<?> raw = cfg.getList("contents");
        if (raw == null) return arr;

        int limit = Math.min(size, raw.size());
        for (int i = 0; i < limit; i++) {
            Object o = raw.get(i);
            if (o instanceof ItemStack is) arr[i] = is;
        }
        return arr;
    }

    private void saveContents(Player p, ItemStack[] contents) {
        ItemStack[] snapshot = contents.clone();

        // 防御：如果某些情况下 locked 外框被写进去了，保存前剥离
        for (int i = 0; i < snapshot.length; i++) {
            ItemStack it = snapshot[i];
            if (it == null || it.getType().isAir()) continue;
            ItemMeta meta = it.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(LOCKED, PersistentDataType.BYTE)) {
                snapshot[i] = null;
            }
        }

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("contents", Arrays.asList(snapshot));

        try {
            cfg.save(dataFile(p));
        } catch (IOException ex) {
            plugin.getLogger().severe("Save failed: " + p.getName());
            ex.printStackTrace();
        }
    }

    private void decrementMainHand(Player p) {
        ItemStack cur = p.getInventory().getItemInMainHand();
        if (cur == null || cur.getType().isAir()) return;

        int amt = cur.getAmount();
        if (amt <= 1) p.getInventory().setItemInMainHand(null);
        else {
            cur.setAmount(amt - 1);
            p.getInventory().setItemInMainHand(cur);
        }
    }

    private boolean isFrameSlot(int slot) {
        List<Integer> frames = plugin.getConfig().getIntegerList("frame.slots");
        if (frames == null || frames.isEmpty()) frames = List.of(0,2,4,6,8);
        return frames.contains(slot);
    }
}
