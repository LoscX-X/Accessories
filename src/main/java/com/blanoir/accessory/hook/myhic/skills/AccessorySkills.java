package com.blanoir.accessory.hook.myhic.skills;

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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AccessorySkills {

    private final Accessory plugin;
    private final NamespacedKey accItemId;
    private final NamespacedKey accItemVersion;
    private final NamespacedKey legacyDunItemId;
    private final Map<String, List<SkillEntry>> skillsByItemId = new HashMap<>();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final Map<String, String> itemIdByName = new HashMap<>();
    private final Map<UUID, PlayerLoadout> loadouts = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, Integer>> accessorySlotSnapshots = new ConcurrentHashMap<>();
    private final Set<UUID> shootHandledProjectiles = ConcurrentHashMap.newKeySet();

    private int skillSignature = 1;
    private long tick = 0;

    public AccessorySkills(Accessory plugin) {
        this.plugin = plugin;
        this.accItemId = new NamespacedKey(plugin, "acc_item_id");
        this.accItemVersion = new NamespacedKey(plugin, "acc_item_version");
        this.legacyDunItemId = new NamespacedKey(plugin, "dun_item_id");
    }

    public void loadConfig() {
        skillsByItemId.clear();
        itemIdByName.clear();

        List<YamlConfiguration> skillConfigs = collectSkillConfigs();
        int signature = 1;

        for (YamlConfiguration cfg : skillConfigs) {
            signature = Math.max(signature, cfg.getInt("signature", 1));

            ConfigurationSection items = cfg.getConfigurationSection("items");
            if (items == null) continue;

            for (String itemId : items.getKeys(false)) {
                String itemName = Objects.toString(cfg.getString("items." + itemId + ".name"), itemId).trim();
                if (!itemName.isEmpty()) {
                    itemIdByName.put(normalizeItemName(itemName), itemId);
                }

                List<?> raw = cfg.getList("items." + itemId + ".skills");
                if (raw == null || raw.isEmpty()) continue;

                List<SkillEntry> parsed = skillsByItemId.computeIfAbsent(itemId, key -> new ArrayList<>());
                for (Object one : raw) {
                    if (!(one instanceof Map<?, ?> m)) continue;
                    String skill = Objects.toString(m.get("skill"), "").trim();
                    TriggerType trigger = TriggerType.from(Objects.toString(m.get("trigger"), ""));
                    if (skill.isEmpty() || trigger == null) continue;

                    int period = toInt(m.get("period"));
                    int cooldown = Math.max(0, toInt(m.get("cooldown")));
                    boolean forceSync = trigger == TriggerType.ON_DEATH && toBoolean(m.get("forcesync"));
                    boolean cancelEvent = trigger == TriggerType.ON_DEATH && toBoolean(m.get("cancelevent"));
                    TargetType target = TargetType.from(Objects.toString(m.get("target"), ""));
                    Object conditions = m.get("conditions");
                    int cdSlot = Math.max(0, Math.min(10, toInt(m.get("cd"))));
                    String cdFormat = Objects.toString(m.get("cd-format"), "").trim();
                    parsed.add(new SkillEntry(skill, trigger, period, cooldown, forceSync, cancelEvent,
                            target, conditions, cdSlot, cdFormat));
                }
            }
        }

        this.skillSignature = signature;
        skillsByItemId.replaceAll((itemId, entries) -> List.copyOf(entries));
        debug("技能配置已重载: files=" + skillConfigs.size() + ", items=" + skillsByItemId.size()
                + ", signature=" + skillSignature);
    }

    private List<YamlConfiguration> collectSkillConfigs() {
        List<YamlConfiguration> configs = new ArrayList<>();

        File rootFile = new File(plugin.getDataFolder(), "skill/skill.yml");
        if (rootFile.exists()) {
            configs.add(YamlConfiguration.loadConfiguration(rootFile));
        }

        File skillFolder = new File(plugin.getDataFolder(), "skill");
        File[] ymlFiles = skillFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (ymlFiles == null || ymlFiles.length == 0) {
            return configs;
        }

        Arrays.sort(ymlFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File ymlFile : ymlFiles) {
            configs.add(YamlConfiguration.loadConfiguration(ymlFile));
        }
        return configs;
    }



    public void startTimer() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick++;
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerLoadout loadout = loadouts.get(p.getUniqueId());
                if (loadout == null || loadout.timers().isEmpty()) continue;
                for (TimerEntry timerEntry : loadout.timers()) {
                    if (tick % timerEntry.period() == 0) {
                        Entity target = timerEntry.entry().target() == TargetType.NONE ? null : p;
                        castIfReady(p, timerEntry.entry(), target, null);
                    }
                }
            }
        }, 1L, 1L);
    }

    public void onQuit(Player player) {
        loadouts.remove(player.getUniqueId());
        cooldowns.remove(player.getUniqueId());
        accessorySlotSnapshots.remove(player.getUniqueId());
    }

    public void refreshFromStored(Player player) {
        refreshPlayer(player, plugin.inventoryStore().getOrLoad(player.getUniqueId(), plugin.totalAccessoryStorageSize()));
    }

    public void refreshPlayer(Player player, Inventory inv) {
        if (inv == null) return;
        refreshPlayer(player, inv.getContents());
    }

    public void refreshPlayer(Player player, ItemStack[] contents, int pageSize) {
        refreshPlayer(player, contents);
    }

    public void refreshPlayer(Player player, ItemStack[] contents) {
        if (player == null || contents == null) return;

        EnumMap<TriggerType, List<ResolvedEntry>> byTrigger = new EnumMap<>(TriggerType.class);
        Set<String> equippedItemIds = new LinkedHashSet<>();
        List<TimerEntry> timers = new ArrayList<>();
        List<ResolvedEntry> allResolved = new ArrayList<>();

        Map<Integer, Integer> previousSnapshot = accessorySlotSnapshots.get(player.getUniqueId());
        Map<Integer, Integer> currentSnapshot = new HashMap<>();

        for (int slot = 0; slot < contents.length; slot++) {
            if (!isAccessorySlot(slot)) continue;

            // 同一物品超过配置的最大装备数量时，多余的副本不注册技能。
            if (plugin.limitManager() != null && !plugin.limitManager().isSlotAllowed(contents, slot)) {
                debug("跳过超出数量限制的饰品: player=" + player.getName() + ", slot=" + slot);
                continue;
            }

            ItemStack item = contents[slot];
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
            }

            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                SkillEntry entry = entries.get(entryIndex);
                TargetType target = entry.target() == null ? TargetType.defaultFor(entry.trigger()) : entry.target();
                if (!target.supports(entry.trigger())) {
                    target = TargetType.defaultFor(entry.trigger());
                }
                String cooldownKey = itemId + ':' + slot + ':' + entryIndex;
                ResolvedEntry resolved = new ResolvedEntry(entry.skill(), entry.trigger(), target,
                        entry.cooldown(), entry.forceSync(), entry.cancelEvent(), cooldownKey,
                        entry.cdSlot(), entry.cdFormat());
                byTrigger.computeIfAbsent(entry.trigger(), k -> new ArrayList<>()).add(resolved);
                allResolved.add(resolved);
                if (entry.trigger() == TriggerType.ON_TIMER) {
                    int period = Math.max(1, entry.period());
                    timers.add(new TimerEntry(resolved, period));
                }
            }
        }

        accessorySlotSnapshots.put(player.getUniqueId(), currentSnapshot);
        loadouts.put(player.getUniqueId(), new PlayerLoadout(
                equippedItemIds, byTrigger, timers, buildCooldownDisplay(allResolved)));
    }

    /**
     * 构建冷却展示槽位（%blacc_cd_1% ~ %blacc_cd_10%）：
     * 配置了 cd 的固定占位；未配置 cd 的按冷却时长升序自动填入剩余槽位（冷却短的在上）。
     */
    private Map<Integer, CooldownDisplayEntry> buildCooldownDisplay(List<ResolvedEntry> entries) {
        Map<Integer, CooldownDisplayEntry> pinned = new LinkedHashMap<>();
        List<ResolvedEntry> auto = new ArrayList<>();

        for (ResolvedEntry entry : entries) {
            if (entry.cdSlot() <= 0 && entry.cdFormat().isEmpty()) {
                continue;
            }
            if (entry.cdSlot() > 0) {
                CooldownDisplayEntry old = pinned.get(entry.cdSlot());
                if (old == null || entry.cooldown() > old.entry().cooldown()) {
                    pinned.put(entry.cdSlot(), new CooldownDisplayEntry(entry, entry.cdFormat()));
                }
            } else {
                auto.add(entry);
            }
        }

        auto.sort(Comparator.comparingInt(ResolvedEntry::cooldown));
        Set<String> seenAuto = new HashSet<>();
        Map<Integer, CooldownDisplayEntry> display = new LinkedHashMap<>(pinned);
        int slot = 1;
        for (ResolvedEntry entry : auto) {
            // 同一技能名只展示一次（多副本时保留冷却最短的那个）。
            if (!seenAuto.add(entry.skill())) {
                continue;
            }
            while (slot <= 10 && display.containsKey(slot)) {
                slot++;
            }
            if (slot > 10) {
                break;
            }
            display.put(slot, new CooldownDisplayEntry(entry, entry.cdFormat()));
            slot++;
        }
        return display;
    }

    /**
     * PlaceholderAPI：%blacc_cd_&lt;1-10&gt;% 的格式化冷却文本。
     * 支持占位符 {cd}=剩余秒 / {max}=总冷却秒 / {skill}=技能名。
     */
    public String formatCooldown(Player player, int slot) {
        if (player == null || slot < 1 || slot > 10) {
            return "";
        }
        PlayerLoadout loadout = loadouts.get(player.getUniqueId());
        if (loadout == null) {
            return "";
        }
        CooldownDisplayEntry display = loadout.cooldownDisplay().get(slot);
        if (display == null) {
            return "";
        }

        ResolvedEntry entry = display.entry();
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        long readyAt = playerCooldowns == null ? 0L : playerCooldowns.getOrDefault(entry.cooldownKey(), 0L);
        long remainingTicks = Math.max(0L, readyAt - tick);
        int seconds = (int) Math.ceil(remainingTicks / 20.0);
        if (entry.cooldown() <= 0) {
            seconds = 0;
        }

        String format = display.format() == null || display.format().isEmpty() ? "{cd}s" : display.format();
        return format.replace("{cd}", String.valueOf(seconds))
                .replace("{max}", String.valueOf(entry.cooldown()))
                .replace("{skill}", entry.skill());
    }

    private boolean isAccessorySlot(int absoluteSlot) {
        int page = plugin.pageManager().pageByAbsoluteSlot(absoluteSlot);
        int slot = plugin.pageManager().localSlot(absoluteSlot);
        return page != -1
                && slot != -1
                && plugin.pageManager().isSlotConfigured(page, slot)
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

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String pdcItemId = pdc.get(accItemId, PersistentDataType.STRING);
        if (pdcItemId != null && !pdcItemId.isBlank()) {
            return pdcItemId;
        }

        String legacyItemId = pdc.get(legacyDunItemId, PersistentDataType.STRING);
        if (legacyItemId != null && !legacyItemId.isBlank()) {
            return legacyItemId;
        }

        if (!meta.hasDisplayName() || meta.displayName() == null) return null;

        return itemIdByName.get(normalizeItemName(PLAIN_TEXT.serialize(meta.displayName())));
    }

    private String normalizeItemName(String raw) {
        return (raw == null ? "" : raw).replaceAll("<[^>]*>", "").trim();
    }

    private boolean stampItem(ItemStack item, String itemId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean dirty = false;
        if (!itemId.equals(pdc.get(accItemId, PersistentDataType.STRING))) {
            pdc.set(accItemId, PersistentDataType.STRING, itemId);
            dirty = true;
        }
        Integer sig = pdc.get(accItemVersion, PersistentDataType.INTEGER);
        if (sig == null || sig != skillSignature) {
            pdc.set(accItemVersion, PersistentDataType.INTEGER, skillSignature);
            dirty = true;
        }
        if (dirty) item.setItemMeta(meta);
        return dirty;
    }

    private boolean needsStamp(ItemStack item, String itemId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String currentId = pdc.get(accItemId, PersistentDataType.STRING);
        Integer currentSig = pdc.get(accItemVersion, PersistentDataType.INTEGER);
        return !itemId.equals(currentId) || currentSig == null || currentSig != skillSignature;
    }

    public boolean stampKnownAccessoryItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String itemId = resolveItemId(item);
        if (itemId == null || itemId.isBlank()) return false;
        return stampItem(item, itemId);
    }

    public void triggerAttack(Player caster, Entity victim) {
        trigger(caster, TriggerType.ON_ATTACK, targetForEvent(TargetType.TARGETED, caster, victim, null), null);
    }

    public void triggerDamaged(Player caster, Entity attacker) {
        trigger(caster, TriggerType.ON_DAMAGED, targetForEvent(TargetType.ATTACKER, caster, null, attacker), null);
    }

    public void triggerKill(Player caster, Entity victim) {
        // onKill 的 MythicMobs trigger 必须是被杀实体，<target.xxx> / <trigger.xxx> 才能取到被杀者
        trigger(caster, TriggerType.ON_KILL, targetForEvent(TargetType.TARGETED, caster, victim, null), victim);
    }

    public void triggerCriticalHit(Player caster, Entity victim) {
        // onCriticalHit 与 onKill 一致：MythicMobs trigger 设为被暴击实体，
        // 这样 <target.xxx> / <trigger.xxx> 都能取到受害者。
        trigger(caster, TriggerType.ON_CRITICAL_HIT,
                targetForEvent(TargetType.TARGETED, caster, victim, null), victim);
    }

    /**
     * Whether the Paper death event should be cancelled before casting death skills.
     * Only true when a cancelevent skill is actually off cooldown,
     * so a skill on cooldown cannot keep the player immortal.
     */
    public boolean shouldCancelDeath(Player caster) {
        PlayerLoadout loadout = loadouts.get(caster.getUniqueId());
        if (loadout == null) return false;

        List<ResolvedEntry> entries = loadout.byTrigger().get(TriggerType.ON_DEATH);
        if (entries == null || entries.isEmpty()) return false;

        Map<String, Long> playerCooldowns = cooldowns.get(caster.getUniqueId());
        for (ResolvedEntry entry : entries) {
            if (!entry.cancelEvent()) continue;
            long readyAt = playerCooldowns == null ? 0L : playerCooldowns.getOrDefault(entry.cooldownKey(), 0L);
            if (tick >= readyAt) return true;
        }
        return false;
    }

    /**
     * Casts all equipped onDeath skills and returns whether any cancelevent skill actually executed.
     */
    public boolean triggerDeath(Player caster) {
        PlayerLoadout loadout = loadouts.get(caster.getUniqueId());
        if (loadout == null) return false;

        List<ResolvedEntry> entries = loadout.byTrigger().get(TriggerType.ON_DEATH);
        if (entries == null || entries.isEmpty()) return false;

        boolean anyCancelCast = false;
        for (ResolvedEntry entry : entries) {
            if (castIfReady(caster, entry, resolveTarget(entry, caster, caster), null) && entry.cancelEvent()) {
                anyCancelCast = true;
            }
        }
        return anyCancelCast;
    }

    /** Clears every accessory skill cooldown for one player. */
    public void refreshAllCooldowns(Player player) {
        if (player != null) cooldowns.remove(player.getUniqueId());
    }

    /** Clears every accessory skill cooldown for every player. */
    public void refreshAllCooldowns() {
        cooldowns.clear();
    }

    public void triggerShoot(Player caster, Entity projectile) {
        if (projectile != null && !shootHandledProjectiles.add(projectile.getUniqueId())) return;
        trigger(caster, TriggerType.ON_SHOOT, targetForEvent(TargetType.PROJECTILE, caster, projectile, null), null);
    }

    public void clearShootFlag(Entity projectile) {
        if (projectile == null) return;
        shootHandledProjectiles.remove(projectile.getUniqueId());
    }

    private Entity targetForEvent(TargetType configured, Player caster, Entity victim, Entity attacker) {
        return switch (configured) {
            case SELF -> caster;
            case TARGETED, PROJECTILE  -> victim;
            case ATTACKER -> attacker;
            case NONE -> null;
        };
    }

    private void trigger(Player caster, TriggerType trigger, Entity eventTarget, Entity triggerEntity) {
        PlayerLoadout loadout = loadouts.get(caster.getUniqueId());
        if (loadout == null) {
            debug("跳过技能触发（没有已加载的饰品）: player=" + caster.getName() + ", trigger=" + trigger);
            return;
        }

        List<ResolvedEntry> entries = loadout.byTrigger().get(trigger);
        if (entries == null || entries.isEmpty()) {
            debug("跳过技能触发（没有匹配技能）: player=" + caster.getName() + ", trigger=" + trigger);
            return;
        }

        debug("触发饰品技能: player=" + caster.getName() + ", trigger=" + trigger
                + ", skills=" + entries.size() + ", eventTarget=" + entityName(eventTarget));

        for (ResolvedEntry entry : entries) {
            castIfReady(caster, entry, resolveTarget(entry, caster, eventTarget), triggerEntity);
        }
    }

    private Entity resolveTarget(ResolvedEntry entry, Player caster, Entity eventTarget) {
        return switch (entry.target()) {
            case SELF -> caster;
            case NONE -> null;
            default -> eventTarget;
        };
    }

    /** Returns whether the skill actually executed this time (not on cooldown). */
    private boolean castIfReady(Player caster, ResolvedEntry entry, Entity target, Entity triggerEntity) {
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(caster.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        long readyAt = playerCooldowns.getOrDefault(entry.cooldownKey(), 0L);
        if (tick < readyAt) {
            debug("跳过技能触发（冷却中）: player=" + caster.getName() + ", skill=" + entry.skill()
                    + ", remaining=" + ((readyAt - tick) / 20.0) + "s");
            return false;
        }
        if (cast(caster, entry, target, triggerEntity)) {
            if (entry.cooldown() > 0) {
                // cooldown 配置单位为秒，内部按 tick 计时
                playerCooldowns.put(entry.cooldownKey(), tick + entry.cooldown() * 20L);
            }
            return true;
        }
        return false;
    }

    private boolean cast(Player caster, ResolvedEntry entry, Entity target, Entity triggerEntity) {
        boolean success = MythicBukkit.inst().getAPIHelper().castSkill(caster, entry.skill(), meta -> {
            if (target != null) {
                meta.setEntityTarget(BukkitAdapter.adapt(target));
            }
            // MM 5.13 的 <target.xxx> 占位符从 trigger 实体解析，而不是 entityTarget；
            // 饰品通过 API 施法时 trigger 默认是 null，必须一并设置，否则 <target.mhp> 等无法解析。
            // onKill 场景下 trigger 固定为被杀实体（可能与技能目标不同，例如 target: self）。
            Entity trigger = triggerEntity != null ? triggerEntity : target;
            if (trigger != null) {
                meta.setTrigger(BukkitAdapter.adapt(trigger));
            }
            if (entry.trigger() == TriggerType.ON_DEATH) {
                // onDeath 默认允许技能在玩家死亡后继续执行
                meta.setExecuteAfterDeath(true);
                if (entry.forceSync()) {
                    meta.setIsAsync(false);
                }
            }
        });
        debug("执行 MythicMobs 技能: player=" + caster.getName() + ", skill=" + entry.skill()
                + ", target=" + entityName(target) + ", success=" + success);
        return success;
    }

    private String entityName(Entity entity) {
        return entity == null ? "none" : entity.getType() + "(" + entity.getUniqueId() + ")";
    }

    private void debug(String message) {
        if (!plugin.getConfig().getBoolean("skill-debug", false)) return;
        plugin.getLogger().info("[SkillDebug] " + message);
    }

    private record PlayerLoadout(Set<String> equippedItemIds,
                                 EnumMap<TriggerType, List<ResolvedEntry>> byTrigger,
                                 List<TimerEntry> timers,
                                 Map<Integer, CooldownDisplayEntry> cooldownDisplay) {
    }

    private record SkillEntry(String skill, TriggerType trigger, int period, int cooldown, boolean forceSync,
                              boolean cancelEvent, TargetType target, Object conditions,
                              int cdSlot, String cdFormat) {
    }

    private record ResolvedEntry(String skill, TriggerType trigger, TargetType target, int cooldown, boolean forceSync,
                                 boolean cancelEvent, String cooldownKey, int cdSlot, String cdFormat) {
    }

    private record TimerEntry(ResolvedEntry entry, int period) {
    }

    private record CooldownDisplayEntry(ResolvedEntry entry, String format) {
    }

    public enum TriggerType {
        ON_ATTACK, ON_CRITICAL_HIT, ON_DAMAGED, ON_SHOOT, ON_KILL, ON_DEATH, ON_TIMER;

        static TriggerType from(String raw) {
            return switch (raw) {
                case "onAttack" -> ON_ATTACK;
                case "onCriticalHit" -> ON_CRITICAL_HIT;
                case "onDamaged" -> ON_DAMAGED;
                case "onShoot" -> ON_SHOOT;
                case "onKill" -> ON_KILL;
                case "onDeath" -> ON_DEATH;
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
                case TARGETED -> trigger == TriggerType.ON_ATTACK
                        || trigger == TriggerType.ON_CRITICAL_HIT
                        || trigger == TriggerType.ON_KILL;
                case ATTACKER -> trigger == TriggerType.ON_DAMAGED;
                case PROJECTILE -> trigger == TriggerType.ON_SHOOT;
            };
        }

        static TargetType defaultFor(TriggerType trigger) {
            return switch (trigger) {
                case ON_ATTACK, ON_CRITICAL_HIT, ON_KILL -> TARGETED;
                case ON_DAMAGED -> ATTACKER;
                case ON_SHOOT -> PROJECTILE;
                case ON_DEATH, ON_TIMER -> SELF;
            };
        }

    }
    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception ignore) {
            return 0;
        }
    }

    private boolean toBoolean(Object val) {
        return val instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(val));
    }
}
