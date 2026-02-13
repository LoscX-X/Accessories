package com.blanoir.accessory.bridge;

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
        // 你客户端发 payload 的 action 就用这个字符串
        if (!"accessory.try_equip".equalsIgnoreCase(e.getAction())) return;

        equip.tryEquipMainHand(e.getPlayer());
    }
}
