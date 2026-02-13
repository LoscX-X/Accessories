package com.blanoir.accessory.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;

public class InvCreate implements InventoryHolder {
    private final JavaPlugin plugin;
    private final Inventory inventory;

    public InvCreate(JavaPlugin plugin) {
        this.plugin = plugin;

        FileConfiguration cfg = plugin.getConfig();

        int size = cfg.getInt("size", 9);
        size = Math.max(9, Math.min(54, size));
        size -= size % 9;

        String mmTitle = cfg.getString("title", "<green>Accessory");
        Component title = MiniMessage.miniMessage().deserialize(mmTitle);

        this.inventory = Bukkit.createInventory(this, size, title);

        // 首次创建就铺一次外框（从配置读取槽位）
        applyFrames();
    }

    @Override public Inventory getInventory() { return this.inventory; }

    public void applyFrames() {
        ItemStack pane = makeLockedItemFromConfig();
        List<Integer> slots = plugin.getConfig().getIntegerList("frame.slots");
        if (slots == null || slots.isEmpty()) slots = List.of(0, 2, 4, 6, 8);

        int invSize = inventory.getSize();
        for (int s : slots) {
            if (s >= 0 && s < invSize) {
                inventory.setItem(s, pane.clone());
            }
        }
    }

    public void applyDisabledSlots(List<Integer> slots) {
        if (slots == null || slots.isEmpty()) return;

        ItemStack pane = makeDisabledItemFromConfig();
        int invSize = inventory.getSize();
        for (int s : slots) {
            if (s >= 0 && s < invSize) {
                inventory.setItem(s, pane.clone());
            }
        }
    }

    /** 生成带 PDC 锁标记的外框物品：类型/CMD/隐藏提示/名称/ lore 均从 config 读，失败回退为黑玻璃 */
    private ItemStack makeLockedItemFromConfig() {
        var cfg = plugin.getConfig();

        // 物品类型（匹配失败回退）
        String typeStr = cfg.getString("frame.item.type", "BLACK_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(typeStr, false); // 支持去除"minecraft:"命名空间
        if (mat == null || !mat.isItem()) {
            plugin.getLogger().warning("[Accessory] Invalid material '" + typeStr
                    + "', fallback to BLACK_STAINED_GLASS_PANE.");
            mat = Material.BLACK_STAINED_GLASS_PANE;
        }

        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();

        // 隐藏 tooltip（1.20.5+），旧版静默忽略
        boolean hide = cfg.getBoolean("frame.item.hide-tooltip", true);
        try { meta.setHideTooltip(hide); } catch (NoSuchMethodError ignored) {}

        // CustomModelData（>0 才设置）
        int cmd = cfg.getInt("frame.item.custom-model-data", -1);
        if (cmd > 0) {
            try { meta.setCustomModelData(cmd); } catch (Throwable t) {
                plugin.getLogger().warning("[Accessory] Failed to set custom-model-data: " + cmd);
            }
        }

        // 显示名（MiniMessage）
        String mmName = cfg.getString("frame.item.name", null);
        if (mmName != null && !mmName.isEmpty()) {
            meta.displayName(MiniMessage.miniMessage().deserialize(mmName));
        }

        // lore（MiniMessage）
        List<String> mmLore = cfg.getStringList("frame.item.lore");
        if (mmLore != null && !mmLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(mmLore.size());
            for (String line : mmLore) {
                lore.add(MiniMessage.miniMessage().deserialize(line));
            }
            try { meta.lore(lore); } catch (NoSuchMethodError ignored) {}
        }

        //“locked”标记
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "locked"),
                PersistentDataType.BYTE, (byte) 1
        );

        it.setItemMeta(meta);
        return it;
    }

    private ItemStack makeDisabledItemFromConfig() {
        var cfg = plugin.getConfig();

        String typeStr = cfg.getString("disabled-slot.item.type", "RED_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(typeStr, false);
        if (mat == null || !mat.isItem()) {
            mat = Material.RED_STAINED_GLASS_PANE;
        }

        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();

        boolean hide = cfg.getBoolean("disabled-slot.item.hide-tooltip", true);
        try { meta.setHideTooltip(hide); } catch (NoSuchMethodError ignored) {}

        String mmName = cfg.getString("disabled-slot.item.name", "<red>Slot Disabled");
        meta.displayName(MiniMessage.miniMessage().deserialize(mmName));

        List<String> mmLore = cfg.getStringList("disabled-slot.item.lore");
        if (mmLore != null && !mmLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(mmLore.size());
            for (String line : mmLore) {
                lore.add(MiniMessage.miniMessage().deserialize(line));
            }
            try { meta.lore(lore); } catch (NoSuchMethodError ignored) {}
        }

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "disabled"),
                PersistentDataType.BYTE, (byte) 1
        );
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "locked"),
                PersistentDataType.BYTE, (byte) 1
        );

        it.setItemMeta(meta);
        return it;
    }
}
