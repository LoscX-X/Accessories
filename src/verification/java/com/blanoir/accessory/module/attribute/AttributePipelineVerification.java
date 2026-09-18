package com.blanoir.accessory.module.attribute;

import com.blanoir.accessory.module.attribute.load.AttributeLoader;
import com.blanoir.accessory.verification.Checks;
import com.blanoir.accessory.verification.TestItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AttributePipelineVerification {
    private static class Loader implements AttributeLoader {
        int last = -1;
        @Override public void rebuild(Player player, ItemStack[] items) { last = items.length; }
    }
    public static void run() {
        for (boolean externalEnabled : new boolean[]{false, true}) for (boolean vanillaEnabled : new boolean[]{false, true}) {
            Loader external = new Loader(), vanilla = new Loader();
            var pipeline = new AccessoryAttributes(externalEnabled ? external : null, vanilla, vanillaEnabled);
            pipeline.rebuild(null, new ItemStack[]{new TestItem("ring")});
            Checks.that(vanilla.last == (vanillaEnabled ? 1 : 0), "vanilla toggle independently applies/cleans");
            Checks.that(external.last == (externalEnabled ? 1 : -1), "only selected external provider executes");
            pipeline.clear(null);
            Checks.that(vanilla.last == 0 && (!externalEnabled || external.last == 0), "clear removes all selected sources");
        }
    }
}
