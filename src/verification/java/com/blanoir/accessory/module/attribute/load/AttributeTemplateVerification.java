package com.blanoir.accessory.module.attribute.load;

import com.blanoir.accessory.verification.Checks;
import com.blanoir.accessory.verification.TestItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class AttributeTemplateVerification {
    public static void run() {
        List<String> calls = new ArrayList<>();
        var nested = new AbstractAttributeLoader<Integer>() {
            int next;
            @Override protected Integer begin(Player player) { return ++next; }
            @Override protected void apply(Integer context, ItemStack item, int slot) {
                calls.add("apply" + context);
                if (context == 1) rebuild(null, new ItemStack[]{item});
            }
            @Override protected void finish(Integer context) { calls.add("finish" + context); }
        };
        nested.rebuild(null, new ItemStack[]{new TestItem("ring")});
        Checks.that(calls.equals(List.of("apply1", "apply2", "finish2", "finish1")), "nested refresh contexts are independent");
        calls.clear();
        var failing = new AbstractAttributeLoader<Integer>() {
            @Override protected Integer begin(Player player) { calls.add("begin"); return 1; }
            @Override protected void apply(Integer context, ItemStack item, int slot) { throw new IllegalArgumentException("injected"); }
            @Override protected void finish(Integer context) { calls.add("finish"); }
        };
        Checks.rejects(() -> failing.rebuild(null, new ItemStack[]{new TestItem("ring")}), "provider error propagates");
        Checks.that(calls.equals(List.of("begin", "finish")), "finish called after provider error");
        calls.clear();failing.clear(null);
        Checks.that(calls.equals(List.of("begin", "finish")), "empty rebuild still clears and publishes");
    }
}
