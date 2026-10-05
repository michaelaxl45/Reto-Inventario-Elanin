package com.store.inventory.stock;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import com.store.inventory.policy.CategoryPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Stock and reservations of a single product.
 *
 * <p>All state is guarded by {@link #lock}, so checking availability and reserving happen as one step.
 * Expired reservations are released lazily, the next time the product is accessed.
 */
final class ProductStock {

    private final String sku;
    private final ProductCategory category;
    private final CategoryPolicy policy;
    private final int lowStockThreshold;
    private final Clock clock;
    private final Lock lock = new ReentrantLock();

    private final Map<String, Reservation> activeReservations = new HashMap<>();
    private final Map<String, Reservation> confirmedReservations = new HashMap<>();
    private int unitsInStock;
    private boolean lowStockAlertSent;

    ProductStock(String sku, ProductCategory category, CategoryPolicy policy, int lowStockThreshold, Clock clock) {
        this.sku = sku;
        this.category = category;
        this.policy = policy;
        this.lowStockThreshold = lowStockThreshold;
        this.clock = clock;
    }

    ProductCategory category() {
        return category;
    }

    Optional<LowStockAlert> addStock(int quantity) {
        lock.lock();
        try {
            releaseExpiredReservations();
            unitsInStock = Math.addExact(unitsInStock, quantity);
            lowStockAlertSent = false;
            return checkLowStock();
        } finally {
            lock.unlock();
        }
    }

    ReserveResult reserve(String orderId, int quantity, OrderRegistry orders) {
        lock.lock();
        try {
            Instant now = releaseExpiredReservations();

            Reservation existing = findReservation(orderId);
            if (existing != null) {
                return repeatedRequest(existing, quantity);
            }
            if (!policy.allows(quantity)) {
                throw new OrderLimitExceededException(sku, quantity, policy.maxUnitsPerOrder().getAsInt());
            }
            int available = availableUnits();
            if (quantity > available) {
                throw new InsufficientStockException(sku, quantity, available);
            }
            if (!orders.bind(orderId, sku)) {
                throw new IllegalStateException("Order " + orderId + " already reserved a different product");
            }

            Reservation reservation = new Reservation(orderId, sku, quantity, now.plus(policy.paymentWindow()));
            activeReservations.put(orderId, reservation);
            return new ReserveResult(reservation, checkLowStock());
        } finally {
            lock.unlock();
        }
    }

    void confirm(String orderId) {
        lock.lock();
        try {
            releaseExpiredReservations();
            Reservation reservation = activeReservations.remove(orderId);
            if (reservation == null) {
                throw new IllegalStateException("Order " + orderId + " has no active reservation");
            }
            unitsInStock -= reservation.quantity();
            confirmedReservations.put(orderId, reservation);
        } finally {
            lock.unlock();
        }
    }

    int available() {
        lock.lock();
        try {
            releaseExpiredReservations();
            return availableUnits();
        } finally {
            lock.unlock();
        }
    }

    private ReserveResult repeatedRequest(Reservation existing, int quantity) {
        if (existing.quantity() != quantity) {
            throw new IllegalStateException("Order " + existing.orderId() + " already reserved "
                    + existing.quantity() + " units of " + sku + ", requested " + quantity);
        }
        return new ReserveResult(existing, Optional.empty());
    }

    private Reservation findReservation(String orderId) {
        Reservation active = activeReservations.get(orderId);
        return active != null ? active : confirmedReservations.get(orderId);
    }

    private Instant releaseExpiredReservations() {
        Instant now = clock.instant();
        activeReservations.values().removeIf(reservation -> !now.isBefore(reservation.expiresAt()));
        return now;
    }

    private int availableUnits() {
        int reservedUnits = activeReservations.values().stream()
                .mapToInt(Reservation::quantity)
                .sum();
        return unitsInStock - reservedUnits;
    }

    private Optional<LowStockAlert> checkLowStock() {
        int available = availableUnits();
        if (lowStockAlertSent || available > lowStockThreshold) {
            return Optional.empty();
        }
        lowStockAlertSent = true;
        return Optional.of(new LowStockAlert(sku, available));
    }
}
