package com.store.inventory;

import static com.store.inventory.Trace.show;
import static com.store.inventory.Trace.step;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.store.inventory.RecordingAlertListener.Alert;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LowStockAlertTest {

    private MutableClock clock;
    private RecordingAlertListener listener;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2025-11-28T10:00:00Z"));
        listener = new RecordingAlertListener();
        service = Inventory.create(clock, listener);
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
    }

    @Test
    void noAlertWhileMoreThanFiveUnitsAreAvailable() {
        step("SKU-1 con 10 unidades, ORDER-1 reserva 4");
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 4);
        show("disponible", service.available("SKU-1"));

        assertTrue(show("avisos", listener.alerts()).isEmpty());
    }

    @Test
    void alertsWhenAvailableUnitsDropToFive() {
        step("SKU-1 con 10 unidades, ORDER-1 reserva 5");
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 5);
        show("disponible", service.available("SKU-1"));

        assertEquals(List.of(new Alert("SKU-1", 5)), show("avisos", listener.alerts()));
    }

    @Test
    void alertsOnlyOnceUntilRestocked() {
        step("SKU-1 con 10 unidades");
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 6);
        show("avisos tras reservar 6 (quedan 4)", listener.alerts());
        service.reserve("ORDER-2", "SKU-1", 2);
        service.reserve("ORDER-3", "SKU-1", 2);
        show("disponible tras 2 reservas mas", service.available("SKU-1"));

        assertEquals(List.of(new Alert("SKU-1", 4)), show("avisos (sigue siendo uno)", listener.alerts()));
    }

    @Test
    void releasedReservationsDoNotCountAsRestock() {
        step("SKU-1 con 10 unidades, ORDER-1 reserva 6 (aviso con 4)");
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 6);

        clock.advance(Duration.ofMinutes(15));
        step("pasan 15 min, se liberan 6 unidades y ORDER-2 reserva 7");
        service.reserve("ORDER-2", "SKU-1", 7);
        show("disponible", service.available("SKU-1"));

        assertEquals(List.of(new Alert("SKU-1", 4)), show("avisos", listener.alerts()));
    }

    @Test
    void alertsAgainAfterRestockWhenStockRunsLowOnceMore() {
        step("SKU-1 con 10 unidades, ORDER-1 reserva 6 (aviso con 4)");
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 6);

        step("se reabastecen 20 unidades y ORDER-2 reserva 20");
        service.addStock("SKU-1", 20);
        service.reserve("ORDER-2", "SKU-1", 20);
        show("disponible", service.available("SKU-1"));

        assertEquals(List.of(new Alert("SKU-1", 4), new Alert("SKU-1", 4)), show("avisos", listener.alerts()));
    }

    @Test
    void restockThatLeavesStockLowAlertsAgain() {
        step("entran 3 unidades y luego 1 mas");
        service.addStock("SKU-1", 3);
        service.addStock("SKU-1", 1);
        show("disponible", service.available("SKU-1"));

        assertEquals(List.of(new Alert("SKU-1", 3), new Alert("SKU-1", 4)), show("avisos", listener.alerts()));
    }

    @Test
    void confirmingDoesNotAlertBecauseAvailableUnitsDoNotChange() {
        step("SKU-1 con 10 unidades, ORDER-1 reserva 4 y paga");
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 4);

        service.confirm("ORDER-1");
        show("disponible", service.available("SKU-1"));

        assertTrue(show("avisos", listener.alerts()).isEmpty());
    }

    @Test
    void resentOrderDoesNotRepeatTheAlert() {
        step("SKU-1 con 10 unidades, ORDER-1 reserva 6 y la app lo reenvia");
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 6);
        service.reserve("ORDER-1", "SKU-1", 6);

        assertEquals(1, show("avisos", listener.alerts()).size());
    }

    @Test
    void failingAlertChannelDoesNotBreakTheReservation() {
        step("el canal de avisos falla (servidor de correo caido)");
        InventoryService failingService = Inventory.create(clock, (sku, available) -> {
            throw new IllegalStateException("mail server down");
        });
        failingService.registerProduct("SKU-1", ProductCategory.STANDARD);
        failingService.addStock("SKU-1", 10);

        show("reserva ORDER-1 (8 u.)", failingService.reserve("ORDER-1", "SKU-1", 8));

        assertEquals(2, show("disponible", failingService.available("SKU-1")));
    }
}
