package com.store.inventory;

import static com.store.inventory.Trace.rejected;
import static com.store.inventory.Trace.show;
import static com.store.inventory.Trace.step;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The app resends an order when the connection is slow, so the same request may arrive more than once.
 */
class RepeatedReservationTest {

    private MutableClock clock;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2025-11-28T10:00:00Z"));
        service = Inventory.create(clock, (sku, available) -> { });
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.registerProduct("SKU-2", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        service.addStock("SKU-2", 10);
        step("SKU-1 y SKU-2 STANDARD con 10 unidades cada uno");
    }

    @Test
    void resentOrderDoesNotReserveTwice() {
        Reservation first = show("reserva ORDER-1", service.reserve("ORDER-1", "SKU-1", 3));
        clock.advance(Duration.ofSeconds(30));
        step("30 s despues la app reenvia el mismo pedido");
        Reservation resent = show("reenvio ORDER-1", service.reserve("ORDER-1", "SKU-1", 3));

        assertEquals(first, resent);
        assertEquals(7, show("disponible (se reservo una sola vez)", service.available("SKU-1")));
    }

    @Test
    void resentOrderSucceedsEvenIfRemainingStockIsNotEnough() {
        show("reserva ORDER-1", service.reserve("ORDER-1", "SKU-1", 6));
        show("reserva ORDER-2", service.reserve("ORDER-2", "SKU-1", 4));
        show("disponible", service.available("SKU-1"));

        step("la app reenvia ORDER-1 aunque ya no queda stock");
        Reservation resent = show("reenvio ORDER-1", service.reserve("ORDER-1", "SKU-1", 6));

        assertEquals(6, resent.quantity());
        assertEquals(0, show("disponible", service.available("SKU-1")));
    }

    @Test
    void resentOrderAfterPaymentReturnsTheConfirmedReservation() {
        Reservation first = show("reserva ORDER-1", service.reserve("ORDER-1", "SKU-1", 3));
        step("ORDER-1 paga y luego llega un reenvio atrasado");
        service.confirm("ORDER-1");

        Reservation resent = show("reenvio ORDER-1", service.reserve("ORDER-1", "SKU-1", 3));

        assertEquals(first, resent);
        assertEquals(7, show("disponible", service.available("SKU-1")));
    }

    @Test
    void sameOrderWithDifferentQuantityIsRejected() {
        show("reserva ORDER-1", service.reserve("ORDER-1", "SKU-1", 3));

        step("ORDER-1 llega otra vez pidiendo 5");
        rejected(IllegalStateException.class, () -> service.reserve("ORDER-1", "SKU-1", 5));
        assertEquals(7, show("disponible", service.available("SKU-1")));
    }

    @Test
    void sameOrderForAnotherProductIsRejected() {
        show("reserva ORDER-1 en SKU-1", service.reserve("ORDER-1", "SKU-1", 3));

        step("ORDER-1 llega otra vez pero para SKU-2");
        rejected(IllegalStateException.class, () -> service.reserve("ORDER-1", "SKU-2", 3));
        assertEquals(10, show("disponible SKU-2", service.available("SKU-2")));
    }

    @Test
    void orderCanReserveAgainAfterItsReservationExpired() {
        Reservation first = show("reserva ORDER-1", service.reserve("ORDER-1", "SKU-1", 3));
        clock.advance(Duration.ofMinutes(15));
        step("pasan 15 min, la reserva vencio y ORDER-1 vuelve a reservar");

        Reservation second = show("nueva reserva ORDER-1", service.reserve("ORDER-1", "SKU-1", 3));

        assertNotEquals(first.expiresAt(), second.expiresAt());
        assertEquals(7, show("disponible", service.available("SKU-1")));
    }

    @Test
    void orderRejectedForLackOfStockCanBeRetried() {
        show("reserva ORDER-1 (todo el stock)", service.reserve("ORDER-1", "SKU-1", 10));
        step("ORDER-2 pide 1");
        rejected(InsufficientStockException.class, () -> service.reserve("ORDER-2", "SKU-1", 1));

        clock.advance(Duration.ofMinutes(15));
        step("pasan 15 min, ORDER-1 no pago y ORDER-2 reintenta");
        show("reserva ORDER-2", service.reserve("ORDER-2", "SKU-1", 1));

        assertEquals(9, show("disponible", service.available("SKU-1")));
    }
}
