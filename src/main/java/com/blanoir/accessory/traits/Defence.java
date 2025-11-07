package com.blanoir.accessory.traits;

import com.blanoir.accessory.attributeload.CustomTraits;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.bukkit.BukkitTraitHandler;
import dev.aurelium.auraskills.api.trait.Trait;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Defence implements BukkitTraitHandler, Listener{
    private final AuraSkillsApi auraSkills;
    private final JavaPlugin plugin;

    // Inject API dependency in constructor
    public Defence(JavaPlugin plugin, AuraSkillsApi auraSkills) {
        this.auraSkills = auraSkills;
        this.plugin = plugin;
    }

    @Override
    public Trait[] getTraits() {
        // An array containing your CustomTrait instance
        return new Trait[] {CustomTraits.DEFENCE};
    }

    @Override
    public double getBaseLevel(Player player, Trait trait) {
        // The base value of your trait when its stat is at level 0, could be a
        // Minecraft default value or values from other plugins
        return 0;
    }

    @Override
    public void onReload(Player player, SkillsUser user, Trait trait) {
        // Method called when the value of the trait's parent stat changes
    }

    // Example implementation of the trait's functionality (not complete)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e){
        EntityDamageEvent.DamageCause cause = e.getCause();
        if(!(e.getEntity() instanceof Player p)) return;
        if(cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK){
            return;
        }

        SkillsUser User = auraSkills.getUser(p.getUniqueId());
        if(User == null || !User.isLoaded()) return;

        double flat = User.getEffectiveTraitLevel(CustomTraits.DEFENCE); // 固定减伤值
        if (flat <= 0) return;


        e.setDamage(Math.max(0.0, e.getDamage() - flat));
            //This machine don t check the vanilla damage machine
    }
}