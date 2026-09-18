package com.blanoir.accessory.verification;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Value-only item for tests; the protected constructor does not require a running Paper server. */
public final class TestItem extends ItemStack {
    static {
        // Headless fixture: Paper 26 resolves Material.isAir through the live block registry.
        // Only this test material's block lookup is stubbed; production item/event code is unchanged.
        try {
            var field = Material.class.getDeclaredField("blockType");
            field.setAccessible(true);
            field.set(Material.STONE, (java.util.function.Supplier<org.bukkit.block.BlockType>) () -> null);
        } catch (ReflectiveOperationException ex) { throw new ExceptionInInitializerError(ex); }
    }
    private final String id;
    private int amount = 1;
    public TestItem(String id) { this.id = id; }
    @Override public Material getType() { return Material.STONE; }
    @Override public int getAmount() { return amount; }
    @Override public void setAmount(int amount) { this.amount = amount; }
    @Override public TestItem clone() { TestItem item = new TestItem(id); item.amount = amount; return item; }
    @Override public boolean equals(Object other) { return other instanceof TestItem item && id.equals(item.id) && amount == item.amount; }
    @Override public int hashCode() { return 31 * id.hashCode() + amount; }
}
