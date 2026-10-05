package com.store.inventory;

import com.store.inventory.alert.FailSafeStockAlertListener;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.StockAlertListener;
import com.store.inventory.policy.CategoryPolicies;
import com.store.inventory.stock.InMemoryInventoryService;
import java.time.Clock;

/**
 * Entry point used by our automated tests. Keep this signature exactly as it is,
 * and build your implementation here.
 */
public final class Inventory {

    static final int LOW_STOCK_THRESHOLD = 5;

    private Inventory() {
    }

    public static InventoryService create(Clock clock, StockAlertListener alertListener) {
        return new InMemoryInventoryService(
                clock,
                CategoryPolicies.defaults(),
                new FailSafeStockAlertListener(alertListener),
                LOW_STOCK_THRESHOLD);
    }
}
