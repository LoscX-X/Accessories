package com.blanoir.accessory.module.skill;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.*;
import com.blanoir.accessory.module.skill.trigger.SkillTrigger;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Active loadouts and trigger dispatch; knows neither YAML parsing nor MythicMobs classes. */
public final class SkillEngine {
    private record Equipped(String key, SkillDefinition definition) { }
    private final Accessory plugin;
    private final SkillBackend backend;
    private final SkillItems items;
    private SkillCatalog catalog;
    private final SkillCooldowns cooldowns = new SkillCooldowns();
    private final Map<UUID, List<Equipped>> loadouts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, AccessoryProfile> equippedProfiles = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> projectiles = new HashMap<>();
    private volatile long tick;
    public SkillEngine(Accessory plugin, SkillBackend backend, SkillCatalog catalog) {
        this.plugin = plugin; this.backend = backend; this.catalog = catalog; items = new SkillItems(plugin);
    }
    public void catalog(SkillCatalog catalog) { this.catalog = catalog; loadouts.clear(); }
    public boolean stamp(ItemStack item) { return items.stamp(item, catalog); }
    public void equip(Player owner, ItemStack[] active) {
        List<Equipped> resolved = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ItemStack item : active) {
            String id = items.identify(item, catalog);
            for (var skill : catalog.items().getOrDefault(id, List.of())) {
                String key = id + ":" + skill.id();
                // Copies of the same accessory share one ability and cooldown.
                if (seen.add(key)) resolved.add(new Equipped(key, skill));
            }
        }
        loadouts.put(owner.getUniqueId(), List.copyOf(resolved));
        equippedProfiles.put(owner.getUniqueId(), plugin.profiles().resolve(owner));
    }
    public void detach(Player owner, boolean endSession) {
        loadouts.remove(owner.getUniqueId());
        equippedProfiles.remove(owner.getUniqueId());
        if (endSession && plugin.settings().skills().resetCooldownsOnQuit()) cooldowns.clear(owner.getUniqueId());
    }
    public void close() { loadouts.clear(); equippedProfiles.clear(); cooldowns.clear(); projectiles.clear(); }
    public void tick() {
        tick++;
        if (tick % 200 == 0) { projectiles.values().removeIf(expiry -> expiry <= tick); cooldowns.expire(tick); }
        for (var entry : List.copyOf(loadouts.entrySet())) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null || owner.isDead()) continue;
            for (Equipped ability : entry.getValue())
                if (ability.definition().trigger() == SkillTrigger.ON_TIMER && tick % ability.definition().period() == 0)
                    cast(owner, ability, owner, null);
        }
    }
    public void trigger(Player owner, SkillTrigger trigger, Entity target, Entity triggerEntity) {
        for (Equipped ability : loadouts.getOrDefault(owner.getUniqueId(), List.of()))
            if (ability.definition().trigger() == trigger) cast(owner, ability, target, triggerEntity);
    }
    public boolean shouldCancelDeath(Player owner) {
        return allowed(owner) && loadouts.getOrDefault(owner.getUniqueId(), List.of()).stream().anyMatch(ability ->
                ability.definition().trigger() == SkillTrigger.ON_DEATH && ability.definition().cancelEvent()
                        && cooldowns.remaining(owner.getUniqueId(), ability.key(), tick) == 0);
    }
    public boolean death(Player owner) {
        boolean cancelled = false;
        for (Equipped ability : loadouts.getOrDefault(owner.getUniqueId(), List.of()))
            if (ability.definition().trigger() == SkillTrigger.ON_DEATH)
                cancelled |= cast(owner, ability, owner, null) && ability.definition().cancelEvent();
        return cancelled;
    }
    public void shoot(Player owner, Entity projectile) {
        if (projectiles.putIfAbsent(projectile.getUniqueId(), tick + 200) == null)
            trigger(owner, SkillTrigger.ON_SHOOT, projectile, null);
    }
    private boolean allowed(Player owner) { return owner.isOnline() && plugin.profiles().isAllowed(owner, AccessoryAction.SKILLS)
            && plugin.profiles().resolve(owner).equals(equippedProfiles.get(owner.getUniqueId())); }
    private boolean cast(Player owner, Equipped ability, Entity eventTarget, Entity trigger) {
        if (!allowed(owner)) return false;
        SkillDefinition skill = ability.definition();
        Entity target = switch (skill.target()) { case SELF -> owner; case NONE -> null; default -> eventTarget; };
        try {
            boolean success = cooldowns.cast(owner.getUniqueId(), ability.key(), skill.cooldown(), tick,
                    () -> backend.cast(owner, skill, target, trigger));
            if (success) plugin.debug().trace("skills", owner.getUniqueId() + ":" + skill.skill(),
                    () -> "cast player=" + owner.getName() + " skill=" + skill.skill() + " trigger=" + skill.trigger().configName());
            return success;
        } catch (RuntimeException | LinkageError ex) {
            plugin.debug().failure("skills", "Failed to cast " + skill.skill(), ex); return false;
        }
    }
    public List<AccessorySkillInfo> skills(Player owner) {
        if (owner == null) return List.of();
        return loadouts.getOrDefault(owner.getUniqueId(), List.of()).stream().map(ability -> {
            int remaining = (int) Math.ceil(cooldowns.remaining(owner.getUniqueId(), ability.key(), tick) / 20.0);
            var skill = ability.definition();
            return new AccessorySkillInfo(skill.skill(), skill.trigger().configName(), skill.cooldown(), remaining, remaining == 0);
        }).toList();
    }
    public String format(Player owner, int slot) {
        if (owner == null || slot < 1 || slot > 10) return "";
        Map<Integer, Equipped> display = new LinkedHashMap<>();
        List<Equipped> automatic = new ArrayList<>();
        for (Equipped ability : loadouts.getOrDefault(owner.getUniqueId(), List.of())) {
            int fixed = ability.definition().displaySlot();
            if (fixed == 0) automatic.add(ability);
            else display.merge(fixed, ability, (a, b) -> a.definition().cooldown() >= b.definition().cooldown() ? a : b);
        }
        automatic.sort(Comparator.comparingInt(ability -> ability.definition().cooldown()));
        int index = 1;
        for (Equipped ability : automatic) {
            while (display.containsKey(index)) index++;
            if (index > 10) break;
            display.put(index++, ability);
        }
        Equipped ability = display.get(slot);
        if (ability == null) return "";
        var definition = ability.definition();
        return definition.displayFormat().replace("{cd}", String.valueOf((int) Math.ceil(cooldowns.remaining(owner.getUniqueId(), ability.key(), tick) / 20.0)))
                .replace("{max}", String.valueOf(definition.cooldown())).replace("{skill}", definition.skill());
    }
    public void reset(Player owner) { if (owner != null) cooldowns.clear(owner.getUniqueId()); }
    public void reset() { cooldowns.clear(); }
}
