package com.store.inventory.policy;

import static com.store.inventory.Trace.rejected;
import static com.store.inventory.Trace.show;
import static com.store.inventory.Trace.step;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.store.inventory.api.ProductCategory;
import java.time.Duration;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CategoryPoliciesTest {

    private final CategoryPolicies policies = CategoryPolicies.defaults();

    @ParameterizedTest
    @EnumSource(ProductCategory.class)
    void everyCategoryHasAPolicy(ProductCategory category) {
        assertNotNull(show("reglas de " + category, policies.policyFor(category)));
    }

    @Test
    void standard() {
        assertEquals(new CategoryPolicy(Duration.ofMinutes(15), OptionalInt.empty()),
                show("STANDARD", policies.policyFor(ProductCategory.STANDARD)));
    }

    @Test
    void preOrder() {
        assertEquals(new CategoryPolicy(Duration.ofHours(24), OptionalInt.empty()),
                show("PRE_ORDER", policies.policyFor(ProductCategory.PRE_ORDER)));
    }

    @Test
    void flashSale() {
        assertEquals(new CategoryPolicy(Duration.ofMinutes(5), OptionalInt.of(2)),
                show("FLASH_SALE", policies.policyFor(ProductCategory.FLASH_SALE)));
    }

    @Test
    void limitIsInclusive() {
        step("limite de 2 unidades por pedido");
        CategoryPolicy policy = CategoryPolicy.limitedTo(2, Duration.ofMinutes(5));

        assertTrue(show("permite 2", policy.allows(2)));
        assertFalse(show("permite 3", policy.allows(3)));
    }

    @Test
    void rejectsInvalidRules() {
        step("tiempo para pagar = 0");
        rejected(IllegalArgumentException.class, () -> CategoryPolicy.unlimited(Duration.ZERO));
        step("limite por pedido = 0");
        rejected(IllegalArgumentException.class, () -> CategoryPolicy.limitedTo(0, Duration.ofMinutes(5)));
    }
}
