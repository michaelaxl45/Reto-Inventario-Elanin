package com.store.inventory.policy;

import com.store.inventory.api.ProductCategory;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Business rules for every product category.
 *
 * <p>To add a category, add it to {@link ProductCategory} and give it a case in {@link #defaultFor}.
 * The switch is exhaustive, so the build fails until the new category has its rules.
 */
public final class CategoryPolicies {

    private final Map<ProductCategory, CategoryPolicy> policies;

    private CategoryPolicies(Map<ProductCategory, CategoryPolicy> policies) {
        this.policies = policies;
    }

    public static CategoryPolicies defaults() {
        Map<ProductCategory, CategoryPolicy> policies = new EnumMap<>(ProductCategory.class);
        for (ProductCategory category : ProductCategory.values()) {
            policies.put(category, defaultFor(category));
        }
        return new CategoryPolicies(policies);
    }

    public CategoryPolicy policyFor(ProductCategory category) {
        return policies.get(category);
    }

    private static CategoryPolicy defaultFor(ProductCategory category) {
        return switch (category) {
            case STANDARD -> CategoryPolicy.unlimited(Duration.ofMinutes(15));
            case PRE_ORDER -> CategoryPolicy.unlimited(Duration.ofHours(24));
            case FLASH_SALE -> CategoryPolicy.limitedTo(2, Duration.ofMinutes(5));
        };
    }
}
