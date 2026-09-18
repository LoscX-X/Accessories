package com.blanoir.accessory.verification;

public final class Checks {
    private static int count;
    private Checks() { }
    public static void that(boolean success, String name) {
        if (!success) throw new AssertionError(name);
        count++;
    }
    public static void rejects(Runnable operation, String name) {
        try { operation.run(); } catch (IllegalArgumentException expected) { count++; return; }
        throw new AssertionError(name);
    }
    public static int count() { return count; }
}
