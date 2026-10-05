package com.store.inventory;

import static com.store.inventory.Trace.rejected;
import static com.store.inventory.Trace.show;
import static com.store.inventory.Trace.step;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryServiceTest {

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = Inventory.create(Clock.systemUTC(), (sku, available) -> { });
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
    }

    @Test
    void reservingReducesAvailableUnits() {
        step("SKU-1 STANDARD con 10 unidades");
        service.addStock("SKU-1", 10);
        show("reserva ORDER-1 (3 u.)", service.reserve("ORDER-1", "SKU-1", 3));
        assertEquals(7, show("disponible", service.available("SKU-1")));
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        step("SKU-1 STANDARD con 2 unidades, ORDER-1 pide 3");
        service.addStock("SKU-1", 2);
        rejected(InsufficientStockException.class, () -> service.reserve("ORDER-1", "SKU-1", 3));
    }

    @Test
    void confirmedUnitsStaySold() {
        step("SKU-1 STANDARD con 5 unidades");
        service.addStock("SKU-1", 5);
        show("reserva ORDER-1 (2 u.)", service.reserve("ORDER-1", "SKU-1", 2));
        step("ORDER-1 paga (confirm)");
        service.confirm("ORDER-1");
        assertEquals(3, show("disponible", service.available("SKU-1")));
    }
}
