package com.blanoir.accessory.command;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.module.attribute.aura.traits.Absorb;
import com.blanoir.accessory.module.attribute.aura.traits.MagicAbsorb;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

/** Command registration and optional shield command bindings. */
public final class AccessoryCommands {
    private final Accessory plugin;
    private final TraitsCommand shield = new TraitsCommand("accessory.shield", "shield");
    private final TraitsCommand magicShield = new TraitsCommand("accessory.magicshield", "magicshield");

    public AccessoryCommands(Accessory plugin) { this.plugin = plugin; }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            registrar.register("accessory", "打开饰品背包或执行管理命令", java.util.List.of("acc", "acre"), new AccessoryInventoryCommand(plugin));
            registrar.register("shield", "调整当前物理护盾", shield);
            registrar.register("magicshield", "调整当前魔法护盾", magicShield);
        });
    }

    public void configureShields(Absorb absorb, MagicAbsorb magicAbsorb) {
        shield.configure(absorb::addShield, absorb::addShieldPercent);
        magicShield.configure(magicAbsorb::addShield, magicAbsorb::addShieldPercent);
    }
}
