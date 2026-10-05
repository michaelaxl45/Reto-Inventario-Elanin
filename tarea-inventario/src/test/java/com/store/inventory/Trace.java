package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.function.Executable;

/**
 * Prints what each test does and what the service returns, so the console shows the scenario step by step.
 */
public final class Trace {

    private Trace() {
    }

    public static void step(String description) {
        System.out.println("   - " + description);
    }

    public static int show(String label, int value) {
        System.out.println("   = " + label + ": " + value);
        return value;
    }

    public static <T> T show(String label, T value) {
        System.out.println("   = " + label + ": " + value);
        return value;
    }

    public static <E extends Throwable> E rejected(Class<E> expected, Executable action) {
        E error = assertThrows(expected, action);
        System.out.println("   x rechazado con " + error.getClass().getSimpleName() + ": " + error.getMessage());
        return error;
    }
}
