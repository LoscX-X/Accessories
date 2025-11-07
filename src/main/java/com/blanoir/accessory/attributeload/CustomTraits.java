package com.blanoir.accessory.attributeload;

import com.blanoir.accessory.Accessory;
import dev.aurelium.auraskills.api.item.ItemContext;
import dev.aurelium.auraskills.api.registry.NamespacedId;
import dev.aurelium.auraskills.api.stat.CustomStat;
import dev.aurelium.auraskills.api.trait.CustomTrait;

public class CustomTraits {

    public static final CustomTrait HEAL_REGENERATION = CustomTrait
            .builder(NamespacedId.of("accessory", "heal_regeneration"))
            .displayName("Health_Regeneration")
            .build();
    public static final CustomTrait DEFENCE = CustomTrait
            .builder(NamespacedId.of("accessory", "defence"))
            .displayName("Defense")
            .build();
    public static final CustomTrait HEAL_DECREASE = CustomTrait
            .builder(NamespacedId.of("accessory", "heal_Decrease"))
            .displayName("Heal_Decrease")
            .build();

    public static final CustomTrait LIFE_STEAL = CustomTrait
            .builder(NamespacedId.of("accessory", "life_steal"))
            .displayName("life_steal")
            .build();

}
