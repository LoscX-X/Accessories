package com.blanoir.accessory.attributeload;

import dev.aurelium.auraskills.api.registry.NamespacedId;
import dev.aurelium.auraskills.api.trait.CustomTrait;

public class CustomTraits {
    public static final CustomTrait HEAL_REGENERATION = CustomTrait
            .builder(NamespacedId.of("accessory", "heal_regeneration"))
            .displayName("Heal_Regeneration")
            .build();
    public static final CustomTrait DEFENCE = CustomTrait
            .builder(NamespacedId.of("accessory", "defence"))
            .displayName("Defence")
            .build();
    public static final CustomTrait HEAL_DECREASE = CustomTrait
            .builder(NamespacedId.of("accessory", "heal_regdecrease"))
            .displayName("Heal_Decrease")
            .build();

    public static final CustomTrait LIFE_STEAL = CustomTrait
            .builder(NamespacedId.of("accessory", "life_steal"))
            .displayName("Life_Steal")
            .build();
    public static final CustomTrait ABSORB = CustomTrait
            .builder(NamespacedId.of("accessory", "absorb"))
            .displayName("Absorb")
            .build();
    public static final CustomTrait MAGICABSORB = CustomTrait
            .builder(NamespacedId.of("accessory", "magicabsorb"))
            .displayName("Magic_Absorb")
            .build();
    public static final CustomTrait HEALTH = CustomTrait
            .builder(NamespacedId.of("accessory", "health"))
            .displayName("Health")
            .build();
}
