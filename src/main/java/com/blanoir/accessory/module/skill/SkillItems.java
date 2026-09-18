package com.blanoir.accessory.module.skill;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** One current identifier: acc_item_id; display name is the explicit optional matching rule. */
public final class SkillItems {
    private final NamespacedKey key;
    public SkillItems(JavaPlugin plugin) { key = new NamespacedKey(plugin, "acc_item_id"); }
    public String identify(ItemStack item, SkillCatalog catalog) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        var meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id != null && !id.isBlank()) return id;
        return meta.displayName() == null ? null : catalog.names().get(SkillCatalog.normalize(PlainTextComponentSerializer.plainText().serialize(meta.displayName())));
    }
    public boolean stamp(ItemStack item, SkillCatalog catalog) {
        String id = identify(item, catalog);
        if (id == null || !catalog.items().containsKey(id)) return false;
        var meta = item.getItemMeta(); meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        item.setItemMeta(meta); return true;
    }
}
