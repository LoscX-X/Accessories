package com.blanoir.accessory.bridge;

import com.blanoir.accessory.Accessory;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AccessorySkillEngine {

    private final Accessory plugin;
    private final NamespacedKey dunItemId;
    private final NamespacedKey dunItemSig;
    private final Map<String, List<SkillEntry>> skillsByItemId = new HashMap<>();
    private final Map<UUID, PlayerLoadout> loadouts = new ConcurrentHashMap<>();
    private final Set<UUID> shootHandledProjectiles = ConcurrentHashMap.newKeySet();

    private int skillSignature = 1;
    private long tick = 0;

    public AccessorySkillEngine(Accessory plugin) {
        this.plugin = plugin;
        this.dunItemId = new NamespacedKey(plugin, "dun_item_id");
        this.dunItemSig = new NamespacedKey(plugin, "dun_item_sig");
    }

    public void loadConfig() {
        skillsByItemId.clear();
        File file = new File(plugin.getDataFolder(), "skill.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.skillSignature = Math.max(1, cfg.getInt("signature", 1));

        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items == null) return;

        for (String itemId : items.getKeys(false)) {
            List<?> raw = cfg.getList("items." + itemId + ".skills");
            if (raw == null || raw.isEmpty()) continue;

            List<SkillEntry> parsed = new ArrayList<>();
            for (Object one : raw) {
                if (!(one instanceof Map<?, ?> m)) continue;
                String skill = Objects.toString(m.get("skill"), "").trim();
                TriggerType trigger = TriggerType.from(Objects.toString(m.get("trigger"), ""));
                if (skill.isEmpty() || trigger == null) continue;

                int period = toInt(m.get("period"), 0);
                TargetType target = TargetType.from(Objects.toString(m.get("target"), ""));
                Object conditions = m.get("conditions");
                parsed.add(new SkillEntry(skill, trigger, period, target, conditions));
            }

            if (!parsed.isEmpty()) {
                skillsByItemId.put(itemId, List.copyOf(parsed));
            }
        }
    }

    private int toInt(Object val, int def) {
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception ignore) {
            return def;
        }
    }

    public void startTimer() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick++;
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerLoadout loadout = loadouts.get(p.getUniqueId());
                if (loadout == null || loadout.timers().isEmpty()) continue;
                for (TimerEntry timerEntry : loadout.timers()) {
                    if (tick % timerEntry.period() == 0) {
                        cast(p, timerEntry.skill(), p);
                    }
                }
            }
        }, 1L, 1L);
    }

    public void onQuit(Player player) {
        loadouts.remove(player.getUniqueId());
    }

    public void refreshFromStored(Player player) {
        int size = plugin.getConfig().getInt("size", 9);
        size = Math.max(9, Math.min(54, size));
        size = size - (size % 9);

        org.bukkit.inventory.Inventory tmp = Bukkit.createInventory(null, size);
        File file = new File(plugin.getDataFolder(), "contains/" + player.getUniqueId() + ".yml");
        if (file.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<?> raw = cfg.getList("contents");
            if (raw != null) {
                for (int i = 0; i < Math.min(size, raw.size()); i++) {
                    Object it = raw.get(i);
                    if (it instanceof ItemStack stack) tmp.setItem(i, stack);
                }
            }
        }
        refreshPlayer(player, tmp);
    }

    public void refreshPlayer(Player player, Inventory inv) {
        if (player == null || inv == null) return;

        EnumMap<TriggerType, List<ResolvedEntry>> byTrigger = new EnumMap<>(TriggerType.class);
        Set<String> equippedItemIds = new LinkedHashSet<>();
        List<TimerEntry> timers = new ArrayList<>();

        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            String itemId = resolveItemId(item);
            if (itemId == null || itemId.isBlank()) continue;
            List<SkillEntry> entries = skillsByItemId.get(itemId);
            if (entries == null || entries.isEmpty()) continue;

            equippedItemIds.add(itemId);
            stampItem(item, itemId);
            inv.setItem(slot, item);

            for (SkillEntry entry : entries) {
                TargetType target = entry.target() == null ? TargetType.defaultFor(entry.trigger()) : entry.target();
                ResolvedEntry resolved = new ResolvedEntry(entry.skill(), entry.trigger(), target);
                byTrigger.computeIfAbsent(entry.trigger(), k -> new ArrayList<>()).add(resolved);
                if (entry.trigger() == TriggerType.ON_TIMER) {
                    int period = Math.max(1, entry.period());
                    timers.add(new TimerEntry(entry.skill(), period));
                }
            }
        }

        loadouts.put(player.getUniqueId(), new PlayerLoadout(equippedItemIds, byTrigger, timers));
    }

    private String resolveItemId(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String id = pdc.get(dunItemId, PersistentDataType.STRING);
        if (id != null && skillsByItemId.containsKey(id)) return id;

        for (NamespacedKey key : pdc.getKeys()) {
            if (!key.getKey().endsWith("item_id")) continue;
            String val = pdc.get(key, PersistentDataType.STRING);
            if (val != null && skillsByItemId.containsKey(val)) return val;
        }
        return null;
    }

    private void stampItem(ItemStack item, String itemId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean dirty = false;
        if (!itemId.equals(pdc.get(dunItemId, PersistentDataType.STRING))) {
            pdc.set(dunItemId, PersistentDataType.STRING, itemId);
            dirty = true;
        }
        Integer sig = pdc.get(dunItemSig, PersistentDataType.INTEGER);
        if (sig == null || sig != skillSignature) {
            pdc.set(dunItemSig, PersistentDataType.INTEGER, skillSignature);
            dirty = true;
        }
        if (dirty) item.setItemMeta(meta);
    }

    public void triggerAttack(Player caster, Entity victim) {
        trigger(caster, TriggerType.ON_ATTACK, targetForEvent(TargetType.VICTIM, caster, victim, null));
    }

    public void triggerDamaged(Player caster, Entity attacker) {
        trigger(caster, TriggerType.ON_DAMAGED, targetForEvent(TargetType.ATTACKER, caster, null, attacker));
    }

    public void triggerKill(Player caster, Entity victim) {
        trigger(caster, TriggerType.ON_KILL, targetForEvent(TargetType.VICTIM, caster, victim, null));
    }

    public void triggerShoot(Player caster, Entity projectile) {
        if (projectile != null && !shootHandledProjectiles.add(projectile.getUniqueId())) return;
        trigger(caster, TriggerType.ON_SHOOT, targetForEvent(TargetType.PROJECTILE, caster, projectile, null));
    }

    public void clearShootFlag(Entity projectile) {
        if (projectile == null) return;
        shootHandledProjectiles.remove(projectile.getUniqueId());
    }

    private Entity targetForEvent(TargetType configured, Player caster, Entity victim, Entity attacker) {
        return switch (configured) {
            case SELF -> caster;
            case VICTIM -> victim;
            case ATTACKER -> attacker;
            case PROJECTILE -> victim;
            case NONE -> null;
        };
    }

    private void trigger(Player caster, TriggerType trigger, Entity eventTarget) {
        PlayerLoadout loadout = loadouts.get(caster.getUniqueId());
        if (loadout == null) return;

        List<ResolvedEntry> entries = loadout.byTrigger().get(trigger);
        if (entries == null || entries.isEmpty()) return;

        for (ResolvedEntry entry : entries) {
            Entity target = switch (entry.target()) {
                case SELF -> caster;
                case NONE -> null;
                default -> eventTarget;
            };
            cast(caster, entry.skill(), target);
        }
    }

    private void cast(Player caster, String skillName, Entity target) {
        MythicBukkit.inst().getAPIHelper().castSkill(caster, skillName, meta -> {
            if (target != null) {
                meta.setEntityTarget(BukkitAdapter.adapt(target));
            }
        });
    }

    private record PlayerLoadout(Set<String> equippedItemIds,
                                 EnumMap<TriggerType, List<ResolvedEntry>> byTrigger,
                                 List<TimerEntry> timers) {
    }

    private record SkillEntry(String skill, TriggerType trigger, int period, TargetType target, Object conditions) {
    }

    private record ResolvedEntry(String skill, TriggerType trigger, TargetType target) {
    }

    private record TimerEntry(String skill, int period) {
    }

    public enum TriggerType {
        ON_ATTACK, ON_DAMAGED, ON_SHOOT, ON_KILL, ON_TIMER;

        static TriggerType from(String raw) {
            return switch (raw) {
                case "onAttack" -> ON_ATTACK;
                case "onDamaged" -> ON_DAMAGED;
                case "onShoot" -> ON_SHOOT;
                case "onKill" -> ON_KILL;
                case "onTimer" -> ON_TIMER;
                default -> null;
            };
        }
    }

    public enum TargetType {
        SELF, VICTIM, ATTACKER, PROJECTILE, NONE;

        static TargetType from(String raw) {
            return switch (raw) {
                case "self" -> SELF;
                case "victim" -> VICTIM;
                case "attacker" -> ATTACKER;
                case "projectile" -> PROJECTILE;
                case "none" -> NONE;
                default -> null;
            };
        }

        static TargetType defaultFor(TriggerType trigger) {
            return switch (trigger) {
                case ON_ATTACK, ON_KILL -> VICTIM;
                case ON_DAMAGED -> ATTACKER;
                case ON_SHOOT -> PROJECTILE;
                case ON_TIMER -> SELF;
            };
        }
    }
}
