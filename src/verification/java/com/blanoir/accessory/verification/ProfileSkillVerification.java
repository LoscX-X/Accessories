package com.blanoir.accessory.verification;

import com.blanoir.accessory.api.*;
import com.blanoir.accessory.module.inventory.InventorySlotLayout;
import com.blanoir.accessory.module.inventory.profile.ProfileDefinitions;
import com.blanoir.accessory.module.skill.*;
import com.blanoir.accessory.module.skill.trigger.SkillTrigger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import java.util.*;

final class ProfileSkillVerification {
    static YamlConfiguration yaml(String text) throws Exception {
        var config = new YamlConfiguration(); config.loadFromString(text); return config;
    }
    static void run() throws Exception {
        var requests = new com.blanoir.accessory.module.inventory.ui.MenuRequests();
        UUID viewerOne = UUID.randomUUID(), viewerTwo = UUID.randomUUID(), ownerOne = UUID.randomUUID(), ownerTwo = UUID.randomUUID();
        var first = requests.begin(viewerOne, ownerOne); var second = requests.begin(viewerTwo, ownerOne);
        Checks.that(!requests.complete(first), "second editor invalidates first owner's pending open");
        Checks.that(requests.complete(second), "current owner request completes once");
        var third = requests.begin(viewerOne, ownerOne); var fourth = requests.begin(viewerOne, ownerTwo);
        Checks.that(!requests.complete(third), "viewer switching owners invalidates old callback");
        Checks.that(requests.complete(fourth), "stale callback cannot cancel replacement");
        var fifth = requests.begin(viewerOne, ownerOne); requests.cancelOwner(ownerOne);
        Checks.that(!requests.complete(fifth), "clear/owner quit invalidates pending open");
        var sixth = requests.begin(viewerOne, ownerOne); requests.cancelViewer(viewerOne);
        Checks.that(!requests.complete(sixth), "viewer quit invalidates pending open");
        var seventh = requests.begin(viewerOne, ownerOne); requests.clear();
        Checks.that(!requests.complete(seventh), "reload invalidates pending windows");
        Checks.that(requests.pendingCount() == 0, "request maps release completed/cancelled viewers");
        var base = ProfileDefinitions.parse("default", yaml("inventory:\n  pages: [default]\nrules:\n  skills: false\n"), null, 1, p -> 9);
        var vip = ProfileDefinitions.parse("vip", yaml("priority: 20\nmatch:\n  permissions: [group.vip]\ninventory:\n  title: VIP\n  pages: [default, extra]\n"), base.rules(), 1, p -> 9);
        var arena = ProfileDefinitions.parse("arena", yaml("priority: 50\nmatch:\n  worlds: [arena]\n  permissions: [group.vip]\ninventory:\n  pages: [arena]\n"), base.rules(), 1, p -> 9);
        var definitions = new ProfileDefinitions(Map.of("default", base, "vip", vip, "arena", arena));
        Checks.that(definitions.select("world", p -> false, null).id().equals("default"), "unmatched group uses default backpack");
        Checks.that(definitions.select("world", p -> true, null).inventory().layouts().equals(List.of("default", "extra")), "VIP receives a different two-page backpack");
        Checks.that(definitions.select("arena", p -> true, null).id().equals("arena"), "highest matching profile wins");
        Checks.that(definitions.select("arena", p -> false, null).id().equals("default"), "world and permission selectors combine with AND");
        Checks.that(definitions.select("arena", p -> true, "vip").id().equals("vip"), "explicit session assignment wins");
        Checks.that(!vip.rules().allows(AccessoryAction.SKILLS), "unspecified rules inherit default");
        var other = ProfileDefinitions.parse("aaa", yaml("priority: 20"), base.rules(), 1, p -> 9);
        Checks.that(new ProfileDefinitions(Map.of("default", base, "vip", vip, "aaa", other)).select("world", p -> true, null).id().equals("aaa"), "profile priority ties use stable id order");
        var invalid = yaml("inventory:\n  pages: []");
        Checks.rejects(() -> ProfileDefinitions.parse("bad", invalid, null, 1, p -> 9), "empty backpack definition rejected");
        invalid.set("inventory.pages", List.of("default")); invalid.set("rules.disabled-slots.1", List.of(54));
        Checks.rejects(() -> ProfileDefinitions.parse("bad", invalid, null, 1, p -> 9), "out of range profile slot rejected");
        ItemStack[] old = new ItemStack[36]; old[0] = new TestItem("first"); old[9] = new TestItem("second-page"); old[35] = new TestItem("tail");
        ItemStack[] stable = InventorySlotLayout.migrateLegacy(old, List.of(9, 27), 54);
        Checks.that(stable.length == 108 && stable[54].equals(old[9]) && stable[80].equals(old[35]), "heterogeneous legacy pages retain addresses");
        Checks.that(stable[54] != old[9], "migrated item is defensive clone");
        Checks.rejects(() -> InventorySlotLayout.migrateLegacy(old, List.of(9), 54), "unknown old layout rejected instead of truncation");
        var config = yaml("items:\n  ring:\n    name: '&bRing'\n    skills:\n      - skill: Cast\n        trigger: onAttack\n        cooldown: 5\n");
        var catalog = SkillCatalog.parse(config);
        var definition = catalog.items().get("ring").getFirst();
        Checks.that(catalog.names().get("Ring").equals("ring"), "color formatting normalized for item matching");
        Checks.that(definition.id().equals("Cast@onAttack") && definition.target() == SkillDefinition.Target.TARGETED, "stable default id and target");
        for (String trigger : List.of("onAttack", "onCriticalHit", "onDamaged", "onShoot", "onKill", "onDeath", "onTimer", "onSkillCast", "onInteract"))
            Checks.that(SkillTrigger.parse(trigger).configName().equals(trigger), "supported trigger " + trigger);
        Checks.rejects(() -> SkillTrigger.parse("onTypo"), "unknown trigger fails validation");
        config.set("items.ring.skills", List.of(Map.of("skill", "Cast", "trigger", "onTimer", "target", "attacker")));
        Checks.rejects(() -> SkillCatalog.parse(config), "incompatible target rejected");
        config.set("items.ring.skills", List.of(Map.of("skill", "Cast", "trigger", "onAttack", "conditions", Map.of())));
        Checks.rejects(() -> SkillCatalog.parse(config), "unused conditions cannot silently bypass a rule");
        config.set("items.ring.skills", List.of(Map.of("skill", "Cast", "trigger", "onAttack", "cooldown", -1)));
        Checks.rejects(() -> SkillCatalog.parse(config), "negative cooldown rejected");
        config.set("items.ring.skills", List.of(Map.of("skill", "Cast", "trigger", "onAttack"), Map.of("skill", "Cast", "trigger", "onAttack")));
        Checks.rejects(() -> SkillCatalog.parse(config), "duplicate stable ability id rejected");
        var cooldowns = new SkillCooldowns(); UUID owner = UUID.randomUUID();
        Checks.that(!cooldowns.cast(owner, "ring:cast", 5, 0, () -> false) && cooldowns.remaining(owner, "ring:cast", 0) == 0, "failed cast consumes no cooldown");
        Checks.that(cooldowns.cast(owner, "ring:cast", 5, 0, () -> !cooldowns.cast(owner, "other", 0, 0, () -> true)), "nested trigger blocked before cooldown publication");
        Checks.that(!cooldowns.cast(owner, "ring:cast", 5, 99, () -> true) && cooldowns.cast(owner, "ring:cast", 5, 100, () -> true), "cooldown exact boundary");
        Checks.rejects(() -> cooldowns.cast(owner, "error", 0, 100, () -> { throw new IllegalArgumentException("cast failed"); }), "cast exception propagated to dispatcher");
        Checks.that(cooldowns.cast(owner, "error", 0, 100, () -> true), "exception releases reentrancy reservation");
        cooldowns.clear(owner);
        Checks.that(cooldowns.remaining(owner, "ring:cast", 100) == 0, "explicit cooldown reset");
    }
}
