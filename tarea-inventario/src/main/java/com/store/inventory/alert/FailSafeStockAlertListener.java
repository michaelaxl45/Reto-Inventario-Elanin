package com.store.inventory.alert;

import com.store.inventory.api.StockAlertListener;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;

/**
 * Keeps a failing alert channel from breaking the inventory operation that triggered the alert.
 */
public final class FailSafeStockAlertListener implements StockAlertListener {

    private static final Logger LOG = System.getLogger(FailSafeStockAlertListener.class.getName());

    private final StockAlertListener delegate;

    public FailSafeStockAlertListener(StockAlertListener delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void onLowStock(String sku, int availableUnits) {
        try {
            delegate.onLowStock(sku, availableUnits);
        } catch (RuntimeException e) {
            LOG.log(Level.ERROR, "Low stock alert for " + sku + " could not be delivered", e);
        }
    }
}
