package com.store.inventory;

import static com.store.inventory.Trace.rejected;
import static com.store.inventory.Trace.show;
import static com.store.inventory.Trace.step;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReservationExpirationTest {

    private static final Instant START = Instant.parse("2025-11-28T10:00:00Z");

    private MutableClock clock;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        service = Inventory.create(clock, (sku, available) -> { });
    }

    @ParameterizedTest
    @CsvSource({
            "STANDARD,   PT15M",
            "PRE_ORDER,  PT24H",
            "FLASH_SALE, PT5M"
    })
    void reservationExpiresAccordingToCategory(ProductCategory category, Duration paymentWindow) {
        step("SKU-1 " + category + ", hora actual " + START);
        service.registerProduct("SKU-1", category);
        service.addStock("SKU-1", 10);

        Reservation reservation = show("reserva ORDER-1", service.reserve("ORDER-1", "SKU-1", 1));

        assertEquals(START.plus(paymentWindow), show("vence", reservation.expiresAt()));
    }

    @Test
    void unitsStayReservedUntilTheDeadline() {
        step("SKU-1 STANDARD con 10 unidades, ORDER-1 reserva 4");
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 4);

        clock.advance(Duration.ofMinutes(15).minusMillis(1));
        step("pasan 14:59.999 min, hora " + clock.instant());

        assertEquals(6, show("disponible (sigue reservado)", service.available("SKU-1")));
    }

    @Test
    void expiredReservationReleasesItsUnits() {
        step("SKU-1 STANDARD con 10 unidades");
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        show("reserva ORDER-1", service.reserve("ORDER-1", "SKU-1", 4));
        show("disponible", service.available("SKU-1"));

        clock.advance(Duration.ofMinutes(15));
        step("pasan 15 min sin pagar, hora " + clock.instant());

        assertEquals(10, show("disponible (reserva liberada)", service.available("SKU-1")));
    }

    @Test
    void releasedUnitsCanBeReservedByAnotherCustomer() {
        step("SKU-1 FLASH_SALE con 2 unidades");
        service.registerProduct("SKU-1", ProductCategory.FLASH_SALE);
        service.addStock("SKU-1", 2);
        show("reserva cliente A", service.reserve("ORDER-1", "SKU-1", 2));
        show("disponible", service.available("SKU-1"));

        clock.advance(Duration.ofMinutes(5));
        step("pasan 5 min, cliente A no pago");
        show("disponible", service.available("SKU-1"));
        show("reserva cliente B", service.reserve("ORDER-2", "SKU-1", 2));

        assertEquals(0, show("disponible", service.available("SKU-1")));
    }

    @Test
    void expiredReservationCannotBeConfirmed() {
        step("SKU-1 STANDARD con 10 unidades, ORDER-1 reserva 4");
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 4);

        clock.advance(Duration.ofMinutes(16));
        step("pasan 16 min, ORDER-1 intenta pagar");

        rejected(IllegalStateException.class, () -> service.confirm("ORDER-1"));
        assertEquals(10, show("disponible", service.available("SKU-1")));
    }

    @Test
    void confirmedUnitsDoNotReturnWhenTheDeadlinePasses() {
        step("SKU-1 STANDARD con 10 unidades, ORDER-1 reserva 4 y paga");
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 4);

        service.confirm("ORDER-1");
        show("disponible tras pagar", service.available("SKU-1"));
        clock.advance(Duration.ofHours(1));
        step("pasa 1 hora");

        assertEquals(6, show("disponible (lo vendido no vuelve)", service.available("SKU-1")));
    }

    @Test
    void reservationCanBeConfirmedOnlyOnce() {
        step("SKU-1 STANDARD con 10 unidades, ORDER-1 reserva 4 y paga");
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 4);
        service.confirm("ORDER-1");

        step("ORDER-1 intenta pagar otra vez");
        rejected(IllegalStateException.class, () -> service.confirm("ORDER-1"));
        assertEquals(6, show("disponible", service.available("SKU-1")));
    }

    @Test
    void unknownOrderCannotBeConfirmed() {
        step("se confirma ORDER-404, que nunca reservo");
        rejected(IllegalStateException.class, () -> service.confirm("ORDER-404"));
    }
}
