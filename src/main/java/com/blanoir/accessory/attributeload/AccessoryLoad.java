package com.blanoir.accessory.attributeload;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.AuraSkillsBukkit;
import dev.aurelium.auraskills.api.item.ItemManager;
import dev.aurelium.auraskills.api.item.ModifierType;
import dev.aurelium.auraskills.api.skill.Skill;
import dev.aurelium.auraskills.api.trait.CustomTrait;
import dev.aurelium.auraskills.api.trait.TraitModifier;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;


public class AccessoryLoad {
    private static final String PREFIX = "accessory:";
    private final AuraSkillsApi api;
    private final ItemManager itemManager;
    public AccessoryLoad(){
        this.api =  AuraSkillsApi.get();
        this.itemManager = AuraSkillsBukkit.get().getItemManager();

    }
public void rebuildFromInventory(Player p, Inventory inv){
    SkillsUser user = api.getUser(p.getUniqueId());
    if (user == null || !user.isLoaded()) return;
    for (String name : Map.copyOf(user.getTraitModifiers()).keySet()) {
        if (name.startsWith(PREFIX)) {
            user.removeTraitModifier(name);
        }
    }
    for(int slot = 0;slot < inv.getSize();slot++){
        ItemStack item = inv.getItem(slot);

        if (item == null|| item.getType() == Material.AIR) continue;
        List<TraitModifier> itemMods  = itemManager.getTraitModifiers(item, ModifierType.ITEM);
        List<TraitModifier> armorMods = itemManager.getTraitModifiers(item, ModifierType.ARMOR);
        applyAll(p, user, itemMods, slot);
        applyAll(p, user, armorMods, slot);
        }
    }
    private void applyAll(Player p, SkillsUser user, List<TraitModifier> mods, int slot) {
        if (mods == null || mods.isEmpty()) return;

        for (TraitModifier m : mods) {
            // 为了可重复重建，这里给每条修饰符生成一个稳定的唯一名
            // 结构例： accessory:slot3/heal_regeneration
            String traitId = m.trait().toString(); // 也可自行拼 NamespacedId
            String name = PREFIX + "slot" + slot + "/" + traitId;

            // 保留原来的 operation 与数值；标记 NonPersistent，避免持久写库
            TraitModifier copy = new TraitModifier(name, m.trait(), m.value(), m.operation() // AuraSkillsModifier.Operation
            );
            copy.setNonPersistent();

            user.addTraitModifier(copy);
        }
    }
}
