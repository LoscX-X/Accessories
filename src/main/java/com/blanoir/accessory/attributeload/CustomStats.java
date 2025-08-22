package com.blanoir.accessory.attributeload;

import dev.aurelium.auraskills.api.item.ItemContext;
import dev.aurelium.auraskills.api.registry.NamespacedId;
import dev.aurelium.auraskills.api.stat.CustomStat;

public class CustomStats {

    //this used to load the stats automaticlly
    public static final CustomStat CUSTOM_STAT = CustomStat
            .builder(NamespacedId.of("accessory", "custom_stat"))
                    .trait(CustomTraits.HEAL_REGENERATION, 0.001) // Dodge chance will increase by 0.5 per dexterity level
                    .trait(CustomTraits.DEFENCE, 0.001)
                    .displayName("custom_stat")
                    .description("this used to load the stats automaticlly.")
                    .color("<green>")
                    .symbol("")
                    .item(ItemContext.builder()
                            .material("lime_stained_glass_pane")
                            .group("lower") // A group defined in AuraSkills/menus/stats.yml
                            .order(2) // The position within that group
                            .build())
                    .build();

}