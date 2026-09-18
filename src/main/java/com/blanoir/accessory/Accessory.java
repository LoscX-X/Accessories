package com.blanoir.accessory;

import com.blanoir.accessory.api.AccessoryQuickEquipService;
import com.blanoir.accessory.api.AccessoryService;
import com.blanoir.accessory.command.AccessoryCommands;
import com.blanoir.accessory.config.AccessorySettings;
import com.blanoir.accessory.events.AccessoryChangeCause;
import com.blanoir.accessory.api.AccessorySkills;
import com.blanoir.accessory.module.attribute.aura.traits.Absorb;
import com.blanoir.accessory.module.attribute.aura.traits.MagicAbsorb;
import com.blanoir.accessory.module.inventory.AccessoryPageManager;
import com.blanoir.accessory.module.inventory.AccessoryStore;
import com.blanoir.accessory.module.inventory.ItemLimitManager;
import com.blanoir.accessory.module.inventory.ui.AccessoryMenuController;
import com.blanoir.accessory.module.lifecycle.AccessoryLifecycle;
import com.blanoir.accessory.runtime.AccessoryRuntime;
import com.blanoir.accessory.utils.lang.Lang;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Plugin entry point and public service facade. Implementation belongs to the runtime's services. */
public final class Accessory extends JavaPlugin {
    private final AccessoryCommands commands = new AccessoryCommands(this);
    private AccessoryRuntime runtime;

    public Accessory() { commands.register(); }

    @Override public void onEnable() {
        runtime = new AccessoryRuntime(this);
        runtime.enable();
    }

    @Override public void onDisable() { if (runtime != null) runtime.close(); }

    public void reloadPluginSettings() { runtime.reload(); }
    public Lang lang() { return runtime.configuration().language(); }
    public AccessorySettings settings() { return runtime.configuration().settings(); }
    public com.blanoir.accessory.api.AccessoryProfiles profiles() { return runtime.profiles(); }
    public AccessoryPageManager pageManager(Player owner) { return runtime.profiles().pages(owner); }
    public AccessoryPageManager pageManager(java.util.UUID owner) {
        Player player = org.bukkit.Bukkit.getPlayer(owner);
        return player == null ? pageManager() : pageManager(player);
    }
    public AccessoryPageManager pageManager() { return runtime.configuration().pages(); }
    public AccessoryStore inventoryStore() { return runtime.store(); }
    public AccessoryService service() { return runtime.service(); }
    public AccessoryQuickEquipService quickEquipService() { return runtime.quickEquip(); }
    public ItemLimitManager limitManager() { return runtime.limits(); }
    public AccessoryMenuController menus() { return runtime.menus(); }
    public AccessoryLifecycle lifecycle() { return runtime.lifecycle(); }
    public AccessorySkills skills() { return runtime.skills(); }
    public com.blanoir.accessory.module.skill.SkillSystem skillSystem() { return runtime.skills(); }
    public com.blanoir.accessory.runtime.DebugLog debug() { return runtime.debug(); }

    public void refreshPlayerEffects(Player player, ItemStack[] contents) {
        lifecycle().refresh(player, contents, null, AccessoryChangeCause.API);
    }

    public void configureShieldCommands(Absorb absorb, MagicAbsorb magicAbsorb) {
        commands.configureShields(absorb, magicAbsorb);
    }

    public List<String> antiUnequipLoreTags() { return settings().antiUnequipLore(); }
    public int accessoryPageStart(int page) { return (page - 1) * 54; }
    public int totalAccessoryStorageSize() { return runtime.profiles() == null ? pageManager().pageCount() * 54 : runtime.profiles().storagePages() * 54; }
}
