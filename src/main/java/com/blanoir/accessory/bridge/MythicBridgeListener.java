package com.blanoir.accessory.bridge;

import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class MythicBridgeListener implements Listener {

    private final JavaPlugin plugin;

    public MythicBridgeListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMechanicLoad(MythicMechanicLoadEvent event) {
        if (event.getMechanicName().equalsIgnoreCase("realattack")) {
            event.register(new RealAttackMechanic(plugin, event));
        }
    }
}
