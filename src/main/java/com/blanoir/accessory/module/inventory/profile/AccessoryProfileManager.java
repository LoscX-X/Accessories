package com.blanoir.accessory.module.inventory.profile;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.*;
import com.blanoir.accessory.events.AccessoryChangeCause;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;

/** Session overrides plus current permission/world selection. It never moves or deletes stored items. */
public final class AccessoryProfileManager implements AccessoryProfiles {
    private final Accessory plugin;
    private ProfileDefinitions definitions;
    private Map<String, com.blanoir.accessory.module.inventory.AccessoryPageManager> inventories = Map.of();
    public com.blanoir.accessory.module.inventory.AccessoryPageManager pages(Player owner) { return inventories.get(resolve(owner).id()); }
    public int storagePages() { return inventories.values().stream().mapToInt(p -> p.pageCount()).max().orElse(1); }
    public void configureInventories(com.blanoir.accessory.module.inventory.AccessoryPageManager base) {
        Map<String, com.blanoir.accessory.module.inventory.AccessoryPageManager> resolved = new HashMap<>();
        for (var profile : definitions.profiles()) resolved.put(profile.id(), base.forProfile(profile.inventory().layouts(), profile.inventory().title()));
        inventories = Map.copyOf(resolved);
    }
    private final Map<UUID, String> assignments = new HashMap<>();
    private final Map<UUID, Set<AccessoryAction>> blocked = new HashMap<>();
    private final Map<UUID, AccessoryProfile> observed = new HashMap<>();
    public AccessoryProfileManager(Accessory plugin, ProfileDefinitions definitions) { this.plugin = plugin; this.definitions = definitions; }
    public void replace(ProfileDefinitions next) {
        definitions = next;
        assignments.values().removeIf(id -> !next.contains(id));
        observed.clear();
    }
    @Override public AccessoryProfile resolve(Player owner) {
        requireMainThread();
        return definitions.select(owner.getWorld().getName(), owner::hasPermission, assignments.get(owner.getUniqueId()));
    }
    @Override public void assign(UUID owner, String id) {
        requireMainThread();
        if (!definitions.contains(id)) throw new IllegalArgumentException("Unknown profile: " + id);
        assignments.put(owner, id); refreshOnline(owner);
    }
    @Override public void clearAssignment(UUID owner) { requireMainThread(); assignments.remove(owner); refreshOnline(owner); }
    @Override public void setBlocked(UUID owner, AccessoryAction action, boolean value) {
        requireMainThread();
        if (value) blocked.computeIfAbsent(owner, k -> EnumSet.noneOf(AccessoryAction.class)).add(action);
        else if (blocked.containsKey(owner)) { blocked.get(owner).remove(action); if (blocked.get(owner).isEmpty()) blocked.remove(owner); }
        refreshOnline(owner);
    }
    @Override public boolean isAllowed(Player owner, AccessoryAction action) {
        return resolve(owner).allows(action) && !blocked.getOrDefault(owner.getUniqueId(), Set.of()).contains(action);
    }
    @Override public boolean isSlotAvailable(Player owner, int page, int slot) {
        return resolve(owner).allowsSlot(page, slot) && !plugin.service().isSlotDisabled(slot)
                && pages(owner).isSlotConfigured(page, slot);
    }
    public void poll(Player owner) {
        AccessoryProfile current = resolve(owner);
        if (!current.equals(observed.put(owner.getUniqueId(), current))) refresh(owner);
    }
    @Override public void refresh(Player owner) {
        requireMainThread();
        observed.put(owner.getUniqueId(), resolve(owner));
        plugin.debug().trace("inventory", "profile:" + owner.getUniqueId(), () -> "profile player=" + owner.getName() + " selected=" + resolve(owner).id());
        plugin.menus().closeForOwner(owner.getUniqueId());
        plugin.lifecycle().load(owner, AccessoryChangeCause.PROFILE);
    }
    private void refreshOnline(UUID owner) { Player player = Bukkit.getPlayer(owner); if (player != null) refresh(player); }
    public void forget(UUID owner) { assignments.remove(owner); blocked.remove(owner); observed.remove(owner); }
    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Accessory profiles require the server thread");
    }
}
