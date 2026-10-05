package com.store.inventory.stock;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Remembers which product each order belongs to. Each order reserves a single product.
 */
final class OrderRegistry {

    private final ConcurrentMap<String, String> skuByOrder = new ConcurrentHashMap<>();

    /**
     * Links the order to the product.
     *
     * @return false if the order is already linked to a different product
     */
    boolean bind(String orderId, String sku) {
        String boundSku = skuByOrder.putIfAbsent(orderId, sku);
        return boundSku == null || boundSku.equals(sku);
    }

    Optional<String> skuOf(String orderId) {
        return Optional.ofNullable(skuByOrder.get(orderId));
    }
}
