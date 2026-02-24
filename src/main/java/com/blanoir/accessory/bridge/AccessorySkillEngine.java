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
import org.bukkit.ChatColor;
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
    private final Map<String, String> itemIdByName = new HashMap<>();
    private final Map<UUID, PlayerLoadout> loadouts = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, Integer>> accessorySlotSnapshots = new ConcurrentHashMap<>();
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
        itemIdByName.clear();
        File file = new File(plugin.getDataFolder(), "skill.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.skillSignature = Math.max(1, cfg.getInt("signature", 1));

        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items == null) return;

        for (String itemId : items.getKeys(false)) {
            String itemName = Objects.toString(cfg.getString("items." + itemId + ".name"), itemId).trim();
            if (!itemName.isEmpty()) {
                itemIdByName.put(normalizeItemName(itemName), itemId);
            }

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
        accessorySlotSnapshots.remove(player.getUniqueId());
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

        Map<Integer, Integer> previousSnapshot = accessorySlotSnapshots.get(player.getUniqueId());
        Map<Integer, Integer> currentSnapshot = new HashMap<>();

        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (!isAccessorySlot(slot)) continue;

            ItemStack item = inv.getItem(slot);
            int itemHash = item == null ? 0 : item.hashCode();
            currentSnapshot.put(slot, itemHash);

            if (item == null || item.getType().isAir()) continue;

            String itemId = resolveItemId(item);
            if (itemId == null || itemId.isBlank()) continue;
            debug("识别到饰品物品: player=" + player.getName() + ", slot=" + slot + ", itemId=" + itemId);
            List<SkillEntry> entries = skillsByItemId.get(itemId);
            if (entries == null || entries.isEmpty()) continue;

            equippedItemIds.add(itemId);

            if ((slotChanged(previousSnapshot, slot, itemHash) || needsStamp(item, itemId))
                    && stampItem(item, itemId)) {
                debug("饰品写入 PDC 成功: player=" + player.getName() + ", slot=" + slot + ", itemId=" + itemId + ", signature=" + skillSignature);
                inv.setItem(slot, item);
            }

            for (SkillEntry entry : entries) {
                TargetType target = entry.target() == null ? TargetType.defaultFor(entry.trigger()) : entry.target();
                if (!target.supports(entry.trigger())) {
                    target = TargetType.defaultFor(entry.trigger());
                }
                ResolvedEntry resolved = new ResolvedEntry(entry.skill(), entry.trigger(), target);
                byTrigger.computeIfAbsent(entry.trigger(), k -> new ArrayList<>()).add(resolved);
                if (entry.trigger() == TriggerType.ON_TIMER) {
                    int period = Math.max(1, entry.period());
                    timers.add(new TimerEntry(entry.skill(), period));
                }
            }
        }

        accessorySlotSnapshots.put(player.getUniqueId(), currentSnapshot);
        loadouts.put(player.getUniqueId(), new PlayerLoadout(equippedItemIds, byTrigger, timers));
    }

    private boolean isAccessorySlot(int slot) {
        return plugin.getConfig().isConfigurationSection("Accessory." + slot)
                && (plugin.service() == null || !plugin.service().isSlotDisabled(slot));
    }

    private boolean slotChanged(Map<Integer, Integer> previousSnapshot, int slot, int currentHash) {
        if (previousSnapshot == null) return false;
        Integer previousHash = previousSnapshot.get(slot);
        return previousHash == null || previousHash != currentHash;
    }

    private String resolveItemId(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String displayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        if (displayName == null || displayName.isBlank()) return null;

        return itemIdByName.get(normalizeItemName(displayName));
    }

    private String normalizeItemName(String raw) {
        return ChatColor.stripColor(raw == null ? "" : raw).trim();
    }

    private boolean stampItem(ItemStack item, String itemId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
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
        return dirty;
    }

    private boolean needsStamp(ItemStack item, String itemId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String currentId = pdc.get(dunItemId, PersistentDataType.STRING);
        Integer currentSig = pdc.get(dunItemSig, PersistentDataType.INTEGER);
        return !itemId.equals(currentId) || currentSig == null || currentSig != skillSignature;
    }

    public boolean stampKnownAccessoryItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String itemId = resolveItemId(item);
        if (itemId == null || itemId.isBlank()) return false;
        return stampItem(item, itemId);
    }

    public void triggerAttack(Player caster, Entity victim) {
        trigger(caster, TriggerType.ON_ATTACK, targetForEvent(TargetType.TARGETED, caster, victim, null));
    }

    public void triggerDamaged(Player caster, Entity attacker) {
        trigger(caster, TriggerType.ON_DAMAGED, targetForEvent(TargetType.ATTACKER, caster, null, attacker));
    }

    public void triggerKill(Player caster, Entity victim) {
        trigger(caster, TriggerType.ON_KILL, targetForEvent(TargetType.TARGETED, caster, victim, null));
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
            case TARGETED -> victim;
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

    private void debug(String message) {
        if (!plugin.getConfig().getBoolean("skill-debug", false)) return;
        plugin.getLogger().info("[SkillDebug] " + message);
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
        SELF, TARGETED, ATTACKER, PROJECTILE, NONE;

        static TargetType from(String raw) {
            return switch (raw) {
                case "self" -> SELF;
                case "targeted" -> TARGETED;
                case "victim" -> TARGETED;
                case "attacker" -> ATTACKER;
                case "projectile" -> PROJECTILE;
                case "none" -> NONE;
                default -> null;
            };
        }

        boolean supports(TriggerType trigger) {
            return switch (this) {
                case SELF, NONE -> true;
                case TARGETED -> trigger == TriggerType.ON_ATTACK || trigger == TriggerType.ON_KILL;
                case ATTACKER -> trigger == TriggerType.ON_DAMAGED;
                case PROJECTILE -> trigger == TriggerType.ON_SHOOT;
            };
        }

        static TargetType defaultFor(TriggerType trigger) {
            return switch (trigger) {
                case ON_ATTACK, ON_KILL -> TARGETED;
                case ON_DAMAGED -> ATTACKER;
                case ON_SHOOT -> PROJECTILE;
                case ON_TIMER -> SELF;
            };
        }
    }
}
