package com.store.inventory.alert;

import com.store.inventory.api.StockAlertListener;
import java.util.List;

/**
 * Sends each low stock alert to several channels (email, chat, etc.).
 * A failure in one channel does not prevent delivery to the others.
 */
public final class CompositeStockAlertListener implements StockAlertListener {

    private final List<StockAlertListener> channels;

    public CompositeStockAlertListener(List<? extends StockAlertListener> channels) {
        this.channels = channels.stream()
                .<StockAlertListener>map(FailSafeStockAlertListener::new)
                .toList();
    }

    public static CompositeStockAlertListener of(StockAlertListener... channels) {
        return new CompositeStockAlertListener(List.of(channels));
    }

    @Override
    public void onLowStock(String sku, int availableUnits) {
        for (StockAlertListener channel : channels) {
            channel.onLowStock(sku, availableUnits);
        }
    }
}
