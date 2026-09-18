package com.blanoir.accessory.verification;

import com.blanoir.accessory.config.*;
import com.blanoir.accessory.module.attribute.AttributePipelineVerification;
import com.blanoir.accessory.module.attribute.load.AttributeTemplateVerification;
import com.blanoir.accessory.module.attribute.load.AttributeLoaders;
import com.blanoir.accessory.module.attribute.parse.VanillaLoreParser;
import com.blanoir.accessory.module.lifecycle.EquipmentChangesVerification;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.List;

public final class VerificationMain {
    public static void main(String[] args) throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        var defaults = AccessorySettings.read(config);
        Checks.that(defaults.attributes().provider() == AttributeProvider.NONE && defaults.attributes().vanilla(), "default is explicit vanilla only");
        for (String provider : List.of("none", "AuraSkills", "AttributePlus", "BlancAttribute")) {
            for (boolean vanilla : new boolean[]{false, true}) {
                config.set("attribute.provider", provider); config.set("attribute.vanilla", vanilla);
                var selected = AccessorySettings.read(config).attributes();
                Checks.that(selected.provider().pluginName().equals(provider) && selected.vanilla() == vanilla, "provider/vanilla independent: " + provider + vanilla);
            }
        }
        config.set("attribute.provider", "auto");
        Checks.rejects(() -> AccessorySettings.read(config), "automatic provider selection rejected");
        config.set("attribute.provider", List.of("AuraSkills", "AttributePlus"));
        Checks.rejects(() -> AccessorySettings.read(config), "multiple external providers rejected");
        Checks.rejects(() -> AttributeProvider.parse("typo"), "unknown provider rejected");
        Checks.that(AttributeLoaders.external(null, AttributeProvider.NONE) == null, "none does not access Bukkit or optional APIs");
        for (String policy : List.of("keep", "drop", "follow-keep-inventory")) Checks.that(AccessoryDeathPolicy.parse(policy) != null, "death policy " + policy);
        Checks.rejects(() -> AccessoryDeathPolicy.parse("typo"), "unknown death policy rejected");
        config.set("attribute.provider", "none"); config.set("attribute.lore.enable", true);
        VanillaLoreParser parser = new VanillaLoreParser(AccessorySettings.read(config).attributes().lore());
        Checks.that(parser.parse(List.of("health: +5")).getFirst().amount() == 5, "positive lore value");
        Checks.that(parser.parse(List.of("health: -5")).getFirst().amount() == -5, "negative lore value retains sign");
        Checks.that(Math.abs(parser.parse(List.of("health: x1.2")).getFirst().amount() - 0.2) < 1e-9, "multiplier conversion");
        Checks.that(parser.parse(List.of("unhealthy: +5")).isEmpty(), "keyword boundary");
        config.set("attribute.lore.enable", false);
        Checks.that(new VanillaLoreParser(AccessorySettings.read(config).attributes().lore()).parse(List.of("health: +5")).isEmpty(), "disabled lore parser");
        AttributePipelineVerification.run();
        AttributeTemplateVerification.run();
        EquipmentChangesVerification.run();
        ProfileSkillVerification.run();
        System.out.println("Accessory verification passed: " + Checks.count() + " assertions");
    }
}
