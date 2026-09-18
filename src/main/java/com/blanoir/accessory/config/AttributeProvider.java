package com.blanoir.accessory.config;

import java.util.Locale;

/** Exactly one external provider; vanilla attributes are configured separately. */
public enum AttributeProvider {
    NONE("none"), AURA_SKILLS("AuraSkills"), ATTRIBUTE_PLUS("AttributePlus"), BLANC_ATTRIBUTE("BlancAttribute");

    private final String pluginName;

    AttributeProvider(String pluginName) { this.pluginName = pluginName; }
    public String pluginName() { return pluginName; }

    public static AttributeProvider parse(String value) {
        String normalized = value == null ? "none" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "none" -> NONE;
            case "auraskills" -> AURA_SKILLS;
            case "attributeplus" -> ATTRIBUTE_PLUS;
            case "blancattribute" -> BLANC_ATTRIBUTE;
            default -> throw new IllegalArgumentException(
                    "attribute.provider must be one of: none, AuraSkills, AttributePlus, BlancAttribute (got '" + value + "')");
        };
    }
}
