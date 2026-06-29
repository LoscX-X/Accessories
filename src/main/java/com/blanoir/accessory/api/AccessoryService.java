package com.blanoir.accessory.api;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.module.attribute.loader.AccessoryLoad;
import com.blanoir.accessory.module.inventory.ui.AccessoryInventoryHolder;
import com.blanoir.accessory.module.inventory.ui.AccessoryInventoryMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AccessoryService {

    private final Accessory plugin;
    private final AccessoryLoad accessoryLoad;
    private final AccessoryInventoryMenu menu;

    private final Set<Integer> disabledSlots = new LinkedHashSet<>();

    public AccessoryService(Accessory plugin) {
        this.plugin = plugin;
        this.accessoryLoad = new AccessoryLoad(plugin);
        this.menu = new AccessoryInventoryMenu(plugin);
    }

    /**
     * 外部 API：启用/禁用某个槽位，并刷新在线玩家已打开的 Accessory UI。
     */
    public void setSlotEnabled(int slot, boolean enabled) {
        int size = accessorySize();
        if (slot < 0 || slot >= size) {
            return;
        }

        synchronized (disabledSlots) {
            if (enabled) {
                disabledSlots.remove(slot);
            } else {
                disabledSlots.add(slot);
            }
        }

        refreshOpenAccessoryInventories();
    }

    /**
     * 外部 API：禁用指定槽位。
     */
    public void applySlotDisable(int slot) {
        setSlotEnabled(slot, false);
    }

    /**
     * 外部 API：取消禁用指定槽位。
     */
    public void removeSlotDisable(int slot) {
        setSlotEnabled(slot, true);
    }

    /**
     * 外部 API：批量设置禁用槽位，并刷新已打开的 Accessory UI。
     */
    public void setDisabledSlots(Collection<Integer> slots) {
        int size = accessorySize();

        synchronized (disabledSlots) {
            disabledSlots.clear();

            if (slots != null) {
                for (Integer slot : slots) {
                    if (slot == null) {
                        continue;
                    }

                    if (slot >= 0 && slot < size) {
                        disabledSlots.add(slot);
                    }
                }
            }
        }

        refreshOpenAccessoryInventories();
    }

    /**
     * 外部 API：读取当前禁用槽位快照。
     */
    public List<Integer> getDisabledSlots() {
        synchronized (disabledSlots) {
            return new ArrayList<>(disabledSlots);
        }
    }

    /**
     * 外部 API：判断槽位是否禁用。
     */
    public boolean isSlotDisabled(int slot) {
        synchronized (disabledSlots) {
            return disabledSlots.contains(slot);
        }
    }

    /**
     * 清空某玩家的 accessory 背包
     * 这里必须走 AccessoryStore，不能直接写 contains/*.yml
     * 否则 MySQL 模式、缓存、异步保存都会被绕开
     */
    public boolean clear(UUID playerId) {
        if (playerId == null) {
            return false;
        }

        int totalSize = plugin.totalAccessoryStorageSize();

        plugin.inventoryStore().clear(playerId, totalSize);
        plugin.inventoryStore().flush(playerId, totalSize);

        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            clearOpenAccessoryInventory(online);

            ItemStack[] empty = new ItemStack[totalSize];

            accessoryLoad.rebuildFromContents(online, empty);

            if (plugin.skillEngine() != null) {
                plugin.skillEngine().refreshPlayer(online, empty);
            }
        }

        return true;
    }

    /**
     * 刷新所有正在打开 Accessory UI 的玩家。
     */
    public void refreshOpenAccessoryInventories() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::refreshOpenAccessoryInventories);
            return;
        }

        List<Integer> disabledSnapshot = getDisabledSlots();

        for (Player online : Bukkit.getOnlinePlayers()) {
            InventoryView view = online.getOpenInventory();
            InventoryHolder holder = view.getTopInventory().getHolder();

            if (!(holder instanceof AccessoryInventoryHolder invHolder)) {
                continue;
            }

            menu.decorate(
                    view.getTopInventory(),
                    invHolder,
                    disabledSnapshot
            );
        }
    }

    private void clearOpenAccessoryInventory(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> clearOpenAccessoryInventory(player));
            return;
        }

        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();

        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof AccessoryInventoryHolder invHolder)) {
            return;
        }

        top.clear();

        menu.decorate(
                top,
                invHolder,
                getDisabledSlots()
        );
    }

    private int accessorySize() {
        if (plugin.pageManager() != null) {
            return plugin.pageManager().maxPageSize();
        }

        return plugin.accessorySize();
    }
}