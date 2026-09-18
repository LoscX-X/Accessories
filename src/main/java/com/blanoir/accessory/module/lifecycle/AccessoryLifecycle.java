package com.blanoir.accessory.module.lifecycle;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.events.*;
import com.blanoir.accessory.module.attribute.AccessoryAttributes;
import com.blanoir.accessory.module.lifecycle.EquipmentChanges.Slot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Owns active equipment and effect lifetimes. Persistence and menu transfers remain separate. */
public final class AccessoryLifecycle {
    private final Accessory plugin;
    private final AccessoryEquipment equipment;
    private final AccessoryTags tags;
    private final Map<UUID, Map<Slot, ItemStack>> equipped = new HashMap<>();
    private final Map<UUID, Object> loads = new HashMap<>();
    private AccessoryAttributes attributes;

    public AccessoryLifecycle(Accessory plugin, AccessoryAttributes attributes) {
        this.plugin = plugin;
        this.attributes = attributes;
        equipment = new AccessoryEquipment(plugin);
        tags = new AccessoryTags(plugin);
    }

    public void load(Player player, AccessoryChangeCause cause) {
        requireMainThread();
        Object request = new Object();
        loads.put(player.getUniqueId(), request);
        int size = plugin.totalAccessoryStorageSize();
        plugin.inventoryStore().getSliceOrLoadAsync(player.getUniqueId(), 0, size, size,
                ignored -> finishLoad(player, cause, request, 0));
    }

    private void finishLoad(Player player, AccessoryChangeCause cause, Object request, int attempt) {
        if (!plugin.isEnabled() || Bukkit.getPlayer(player.getUniqueId()) != player || loads.get(player.getUniqueId()) != request) return;
        if (player.isDead()) { loads.remove(player.getUniqueId(), request); return; }
        if (!attributes.isReady(player)) {
            if (attempt < 100) Bukkit.getScheduler().runTaskLater(plugin, () -> finishLoad(player, cause, request, attempt + 1), 2L);
            else {
                loads.remove(player.getUniqueId(), request);
                plugin.getLogger().warning("Attribute data did not become ready for " + player.getUniqueId());
            }
            return;
        }
        loads.remove(player.getUniqueId(), request);
        // Read the current cache; the asynchronous loading snapshot may predate clear/equip.
        refresh(player, plugin.inventoryStore().getOrLoad(player.getUniqueId(), plugin.totalAccessoryStorageSize()), null, cause);
    }

    public void refresh(Player player, ItemStack[] stored, Player actor, AccessoryChangeCause cause) {
        requireMainThread();
        ItemStack[] active = equipment.activeContents(player, stored == null ? new ItemStack[0] : stored);
        Map<Slot, ItemStack> next = snapshot(active);
        Map<Slot, ItemStack> previous = equipped.getOrDefault(player.getUniqueId(), Map.of());
        attributes.rebuild(player, plugin.profiles().isAllowed(player, com.blanoir.accessory.api.AccessoryAction.ATTRIBUTES) ? active : new ItemStack[0]);
        tags.rebuild(player, active);
        plugin.skillSystem().equip(player, plugin.profiles().isAllowed(player, com.blanoir.accessory.api.AccessoryAction.SKILLS) ? active : new ItemStack[0]);
        equipped.put(player.getUniqueId(), next);
        plugin.debug().trace("lifecycle", player.getUniqueId().toString(), () -> "refresh player=" + player.getName() + " cause=" + cause + " active=" + next.size());
        notifyChanges(player, actor, previous, next, cause);
    }

    public void unload(Player player, AccessoryChangeCause cause) {
        requireMainThread();
        loads.remove(player.getUniqueId());
        Map<Slot, ItemStack> previous = equipped.remove(player.getUniqueId());
        attributes.clear(player);
        tags.rebuild(player, new ItemStack[0]);
        plugin.skillSystem().detach(player, cause);
        notifyChanges(player, null, previous == null ? Map.of() : previous, Map.of(), cause);
    }

    public void replaceAttributes(AccessoryAttributes replacement) {
        requireMainThread();
        for (Player player : Bukkit.getOnlinePlayers()) attributes.clear(player);
        attributes = replacement;
        loads.clear();
    }

    public void invalidateLoad(UUID owner) { loads.remove(owner); }

    private Map<Slot, ItemStack> snapshot(ItemStack[] active) {
        Map<Slot, ItemStack> out = new LinkedHashMap<>();
        for (int i = 0; i < active.length; i++) if (active[i] != null) {
            out.put(new Slot(i / 54 + 1, i % 54), active[i].clone());
        }
        return out;
    }

    private void notifyChanges(Player owner, Player actor, Map<Slot, ItemStack> previous,
                               Map<Slot, ItemStack> next, AccessoryChangeCause cause) {
        EquipmentChanges.between(owner, actor, previous, next, cause).forEach(Bukkit.getPluginManager()::callEvent);
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Accessory lifecycle operations require the server thread");
    }

}
