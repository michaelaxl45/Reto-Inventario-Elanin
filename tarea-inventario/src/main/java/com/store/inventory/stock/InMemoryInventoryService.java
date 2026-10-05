package com.store.inventory.stock;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import com.store.inventory.api.StockAlertListener;
import com.store.inventory.policy.CategoryPolicies;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Inventory kept in memory. Each product is locked independently, so orders for
 * different products never wait on each other.
 *
 * <p>Low stock alerts are delivered after the product lock is released, so a slow
 * alert channel does not hold back other orders.
 */
public final class InMemoryInventoryService implements InventoryService {

    private final Clock clock;
    private final CategoryPolicies policies;
    private final StockAlertListener alertListener;
    private final int lowStockThreshold;

    private final ConcurrentMap<String, ProductStock> products = new ConcurrentHashMap<>();
    private final OrderRegistry orders = new OrderRegistry();

    public InMemoryInventoryService(Clock clock, CategoryPolicies policies,
                                    StockAlertListener alertListener, int lowStockThreshold) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.alertListener = Objects.requireNonNull(alertListener, "alertListener");
        this.lowStockThreshold = lowStockThreshold;
    }

    @Override
    public void registerProduct(String sku, ProductCategory category) {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(category, "category");

        ProductStock product = products.computeIfAbsent(sku, key ->
                new ProductStock(key, category, policies.policyFor(category), lowStockThreshold, clock));
        if (product.category() != category) {
            throw new IllegalArgumentException("Product " + sku + " is already registered as " + product.category());
        }
    }

    @Override
    public void addStock(String sku, int quantity) {
        requirePositive(quantity);
        ProductStock product = products.get(Objects.requireNonNull(sku, "sku"));
        if (product == null) {
            throw new IllegalArgumentException("Product " + sku + " is not registered");
        }
        product.addStock(quantity).ifPresent(this::publish);
    }

    @Override
    public Reservation reserve(String orderId, String sku, int quantity) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sku, "sku");
        requirePositive(quantity);

        ProductStock product = products.get(sku);
        if (product == null) {
            throw new InsufficientStockException(sku, quantity, 0);
        }
        ReserveResult result = product.reserve(orderId, quantity, orders);
        result.alert().ifPresent(this::publish);
        return result.reservation();
    }

    @Override
    public void confirm(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        String sku = orders.skuOf(orderId)
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " has no active reservation"));
        products.get(sku).confirm(orderId);
    }

    @Override
    public int available(String sku) {
        ProductStock product = products.get(Objects.requireNonNull(sku, "sku"));
        return product == null ? 0 : product.available();
    }

    private void publish(LowStockAlert alert) {
        alertListener.onLowStock(alert.sku(), alert.availableUnits());
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
    }
}
