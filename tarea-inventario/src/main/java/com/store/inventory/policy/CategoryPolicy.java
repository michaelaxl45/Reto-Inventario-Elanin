package com.store.inventory.policy;

import java.time.Duration;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Reservation rules for a product category: how long the customer has to pay
 * and how many units a single order may reserve.
 */
public record CategoryPolicy(Duration paymentWindow, OptionalInt maxUnitsPerOrder) {

    public CategoryPolicy {
        Objects.requireNonNull(paymentWindow, "paymentWindow");
        Objects.requireNonNull(maxUnitsPerOrder, "maxUnitsPerOrder");
        if (paymentWindow.isZero() || paymentWindow.isNegative()) {
            throw new IllegalArgumentException("Payment window must be positive: " + paymentWindow);
        }
        if (maxUnitsPerOrder.isPresent() && maxUnitsPerOrder.getAsInt() <= 0) {
            throw new IllegalArgumentException("Max units per order must be positive: " + maxUnitsPerOrder.getAsInt());
        }
    }

    public static CategoryPolicy unlimited(Duration paymentWindow) {
        return new CategoryPolicy(paymentWindow, OptionalInt.empty());
    }

    public static CategoryPolicy limitedTo(int maxUnitsPerOrder, Duration paymentWindow) {
        return new CategoryPolicy(paymentWindow, OptionalInt.of(maxUnitsPerOrder));
    }

    public boolean allows(int quantity) {
        return maxUnitsPerOrder.isEmpty() || quantity <= maxUnitsPerOrder.getAsInt();
    }
}
