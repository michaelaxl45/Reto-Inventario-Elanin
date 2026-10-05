package com.store.inventory.stock;

import com.store.inventory.api.Reservation;
import java.util.Optional;

record ReserveResult(Reservation reservation, Optional<LowStockAlert> alert) {
}
