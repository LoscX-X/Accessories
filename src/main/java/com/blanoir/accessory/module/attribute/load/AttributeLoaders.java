package com.blanoir.accessory.module.attribute.load;

import com.blanoir.accessory.config.AttributeProvider;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Explicit selection only. An unavailable selected provider is a configuration error. */
public final class AttributeLoaders {
    private AttributeLoaders() { }

    public static AttributeLoader external(JavaPlugin plugin, AttributeProvider provider) {
        if (provider == AttributeProvider.NONE) return null;
        if (!Bukkit.getPluginManager().isPluginEnabled(provider.pluginName())) {
            throw new IllegalArgumentException("attribute.provider=" + provider.pluginName() + " requires that plugin to be enabled");
        }
        try {
            return switch (provider) {
                case NONE -> null;
                case AURA_SKILLS -> new AuraSkillsLoad(java.util.Objects.requireNonNull(AuraSkillsApi.get(), "AuraSkills API unavailable"));
                case ATTRIBUTE_PLUS -> new AttributePlusLoad(plugin);
                case BLANC_ATTRIBUTE -> new BlancAttributeLoad();
            };
        } catch (LinkageError | RuntimeException ex) {
            throw new IllegalArgumentException("Cannot initialize configured attribute provider " + provider.pluginName(), ex);
        }
    }
}
