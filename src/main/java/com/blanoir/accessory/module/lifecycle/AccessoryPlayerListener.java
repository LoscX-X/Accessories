package com.blanoir.accessory.module.lifecycle;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.config.AccessoryDeathPolicy;
import com.blanoir.accessory.events.AccessoryChangeCause;
import com.blanoir.accessory.events.AccessoryDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

/** Player session and death policy adapter; contains no attribute-provider implementation. */
public final class AccessoryPlayerListener implements Listener {
    private final Accessory plugin;
    public AccessoryPlayerListener(Accessory plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { plugin.lifecycle().load(event.getPlayer(), AccessoryChangeCause.JOIN); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.menus().closeForOwner(player.getUniqueId());
        try { plugin.lifecycle().unload(player, AccessoryChangeCause.QUIT); }
        finally {
            plugin.menus().forgetViewer(player.getUniqueId());
            ((com.blanoir.accessory.module.inventory.profile.AccessoryProfileManager) plugin.profiles()).forget(player.getUniqueId());
            plugin.inventoryStore().saveAndRemove(player.getUniqueId(), plugin.totalAccessoryStorageSize());
        }
    }

    @EventHandler public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        plugin.profiles().refresh(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) plugin.lifecycle().load(event.getPlayer(), AccessoryChangeCause.RESPAWN);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.menus().closeForOwner(player.getUniqueId());
        ItemStack[] contents = plugin.inventoryStore().getOrLoad(player.getUniqueId(), plugin.totalAccessoryStorageSize());
        AccessoryDeathEvent policyEvent = new AccessoryDeathEvent(player, contents, plugin.settings().deathPolicy());
        Bukkit.getPluginManager().callEvent(policyEvent);
        boolean drop = policyEvent.getPolicy() == AccessoryDeathPolicy.DROP
                || policyEvent.getPolicy() == AccessoryDeathPolicy.FOLLOW_KEEP_INVENTORY && !event.getKeepInventory();
        if (drop) {
            plugin.service().clear(player.getUniqueId(), player, AccessoryChangeCause.DEATH);
            for (ItemStack item : contents) if (item != null && !item.getType().isAir()) event.getDrops().add(item.clone());
        }
        plugin.lifecycle().unload(player, AccessoryChangeCause.DEATH);
    }
}
