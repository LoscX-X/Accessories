package com.blanoir.accessory.attributeload;
import com.blanoir.accessory.utils.LoreUtils;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.AuraSkillsBukkit;
import dev.aurelium.auraskills.api.item.ItemManager;
import dev.aurelium.auraskills.api.item.ModifierType;
import dev.aurelium.auraskills.api.trait.TraitModifier;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
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

        // 清除旧 trait modifier
        for (String name : Map.copyOf(user.getTraitModifiers()).keySet()) {
            if (name.startsWith(PREFIX)) {
                user.removeTraitModifier(name);
            }
        }

        // 清除旧 tag
        for (String t : new ArrayList<>(p.getScoreboardTags())) {
            if (t.startsWith("acc_")) {
                p.removeScoreboardTag(t);
            }
        }

        // 循环物品
        for(int slot = 0; slot < inv.getSize(); slot++){
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;

            // ① 识别 Trait modifier
            List<TraitModifier> itemMods  = itemManager.getTraitModifiers(item, ModifierType.ITEM);
            List<TraitModifier> armorMods = itemManager.getTraitModifiers(item, ModifierType.ARMOR);
            applyAll(p, user, itemMods, slot);
            applyAll(p, user, armorMods, slot);

            // ② 识别 Lore 中的 Tags 并加入
            for (String tag : parseTags(item)) {
                p.addScoreboardTag(tag);
            }
        }
    }

    private void applyAll(Player p, SkillsUser user, List<TraitModifier> mods, int slot) {
        if (mods == null || mods.isEmpty()) return;

        for (TraitModifier m : mods) {
            // 结构例： accessory:slot3/heal_regeneration
            String traitId = m.trait().toString(); // 也可自行拼 NamespacedId
            String name = PREFIX + "slot" + slot + "/" + traitId;

            TraitModifier copy = new TraitModifier(name, m.trait(), m.value(), m.operation() // AuraSkillsModifier.Operation
            );
            copy.setNonPersistent();

            user.addTraitModifier(copy);
        }
    }
    //为玩家添加Tags
    private List<String> parseTags(ItemStack item) {
        List<String> lore = LoreUtils.plainLore(item);
        List<String> out = new ArrayList<>();
        boolean inTags = false;

        for (String line : lore) {
            line = line.trim();
            if (line.equalsIgnoreCase("tags:")) {
                inTags = true;
                continue;
            }
            if (!inTags) continue;

            // 检测 [xxx]
            int start = 0;
            while (true) {
                int l = line.indexOf('[', start);
                if (l == -1) break;
                int r = line.indexOf(']', l + 1);
                if (r == -1) break;

                String tag = line.substring(l + 1, r).trim();
                if (!tag.isEmpty()) {
                    out.add("acc_" + tag);
                }
                start = r + 1;
            }
        }
        return out;
    }

}
