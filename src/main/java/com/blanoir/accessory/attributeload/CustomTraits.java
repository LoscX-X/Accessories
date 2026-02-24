package com.blanoir.accessory.attributeload;

import dev.aurelium.auraskills.api.registry.NamespacedId;
import dev.aurelium.auraskills.api.trait.CustomTrait;

public class CustomTraits {
    public static final CustomTrait HEAL_REGENERATION = CustomTrait
            .builder(NamespacedId.of("accessory", "heal_regeneration"))
            .displayName("生命恢复")
            .build();
    public static final CustomTrait DEFENCE = CustomTrait
            .builder(NamespacedId.of("accessory", "defence"))
            .displayName("抵御")
            .build();
    public static final CustomTrait HEAL_DECREASE = CustomTrait
            .builder(NamespacedId.of("accessory", "heal_regdecrease"))
            .displayName("生命恢复衰减")
            .build();

    public static final CustomTrait LIFE_STEAL = CustomTrait
            .builder(NamespacedId.of("accessory", "life_steal"))
            .displayName("life_steal")
            .build();
    public static final CustomTrait ABSORB = CustomTrait
            .builder(NamespacedId.of("accessory", "absorb"))
            .displayName("护盾")
            .build();
    public static final CustomTrait MAGICABSORB = CustomTrait
            .builder(NamespacedId.of("accessory", "magicabsorb"))
            .displayName("魔法护盾")
            .build();
    public static final CustomTrait HEALTH = CustomTrait
            .builder(NamespacedId.of("accessory", "health"))
            .displayName("生命值")
            .build();
}
