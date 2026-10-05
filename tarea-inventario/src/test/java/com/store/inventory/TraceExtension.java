package com.store.inventory;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Prints a header before each test and its result after it. Registered for all tests
 * through {@code META-INF/services}.
 */
public final class TraceExtension implements BeforeEachCallback, AfterTestExecutionCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        String method = context.getRequiredTestMethod().getName();
        String displayName = context.getDisplayName();
        String name = displayName.startsWith(method) ? method : method + " " + displayName;

        System.out.println();
        System.out.println(">> " + context.getRequiredTestClass().getSimpleName() + " > " + name);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        System.out.println(context.getExecutionException().isPresent() ? "<< FALLO" : "<< OK");
    }
}
