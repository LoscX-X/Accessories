package com.blanoir.accessory.bridge.keybind;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.AccessoryQuickEquipService;
import com.blanoir.keybind.event.KeyActionEvent; // ✅ 需要你把 KeyBind 作为 compileOnly / depend
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class AccessoryKeybindHook implements Listener {

    private final AccessoryQuickEquipService equip;

    public AccessoryKeybindHook(Accessory plugin) {
        this.equip = new AccessoryQuickEquipService(plugin);
    }

    @EventHandler
    public void onKey(KeyActionEvent e) {
        if (!"accessory.try_equip".equalsIgnoreCase(e.getAction())) return;

        equip.tryEquipMainHand(e.getPlayer());
    }
}
