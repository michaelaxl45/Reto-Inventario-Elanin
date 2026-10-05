package com.store.inventory;

import com.store.inventory.api.StockAlertListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class RecordingAlertListener implements StockAlertListener {

    record Alert(String sku, int availableUnits) {
    }

    private final List<Alert> alerts = new CopyOnWriteArrayList<>();

    @Override
    public void onLowStock(String sku, int availableUnits) {
        alerts.add(new Alert(sku, availableUnits));
    }

    List<Alert> alerts() {
        return List.copyOf(alerts);
    }
}
