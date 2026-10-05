package com.store.inventory;

import static com.store.inventory.Trace.rejected;
import static com.store.inventory.Trace.show;
import static com.store.inventory.Trace.step;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de la categoría PRE_ORDER (preventa).
 *
 * <p>Según el README, en PRE_ORDER el cliente tiene 24 horas para pagar,
 * porque paga por transferencia bancaria. Mientras no pague, sus unidades
 * quedan apartadas y nadie más puede comprarlas.
 *
 * <p>Cada paso imprime en consola lo que devolvió el servicio y lo que se esperaba,
 * y al final cada test imprime su conclusión.
 */
class PreOrderReservationTest {

    // Fecha y hora fija de inicio, para que el resultado sea siempre el mismo.
    private static final Instant START = Instant.parse("2025-11-28T10:00:00Z");

    // Tiempo que tiene el cliente para pagar en PRE_ORDER.
    private static final Duration PAYMENT_WINDOW = Duration.ofHours(24);

    // Reloj de prueba: permite adelantar la hora sin esperar de verdad.
    private MutableClock clock;

    // El servicio de inventario que se va a probar.
    private InventoryService service;

    @BeforeEach
    void setUp() {
        // Antes de cada test: reloj en la hora de inicio y servicio nuevo, sin datos.
        clock = new MutableClock(START);
        // El segundo parámetro recibe los avisos de stock bajo; aquí no se usan.
        service = Inventory.create(clock, (sku, available) -> { });

        // Se registra un producto de preventa con 10 unidades en almacén.
        service.registerProduct("PRE-1", ProductCategory.PRE_ORDER);
        service.addStock("PRE-1", 10);
        step("PRE-1 PRE_ORDER con 10 unidades, hora actual " + START);
    }

    @Test
    @DisplayName("PRE_ORDER: si no paga en 24 horas, la reserva se libera")
    void unpaidPreOrderIsReleasedAfter24Hours() {
        // Paso 1: el cliente A reserva 4 unidades.
        Reservation reservation = show("reserva cliente A", service.reserve("ORDER-A", "PRE-1", 4));

        // Paso 2: la reserva debe vencer exactamente 24 horas después de crearse.
        verify("vence", START.plus(PAYMENT_WINDOW), reservation.expiresAt());

        // Paso 3: de 10 unidades, 4 están apartadas, así que quedan 6 disponibles.
        verify("disponible", 6, service.available("PRE-1"));

        // Paso 4: pasan 15 minutos. En STANDARD ya habría vencido,
        // pero en PRE_ORDER las unidades siguen apartadas.
        clock.advance(Duration.ofMinutes(15));
        step("pasan 15 min, hora " + clock.instant());
        verify("disponible (sigue reservado)", 6, service.available("PRE-1"));

        // Paso 5: se adelanta el reloj hasta 1 milisegundo antes de cumplir las 24 horas.
        // La reserva todavía está vigente.
        clock.advance(PAYMENT_WINDOW.minusMinutes(15).minusMillis(1));
        step("pasan 23:59:59.999, hora " + clock.instant());
        verify("disponible (sigue reservado)", 6, service.available("PRE-1"));

        // Paso 6: el cliente B pide 8, pero solo hay 6 libres. Se rechaza,
        // porque las 4 del cliente A siguen apartadas.
        step("cliente B pide 8 unidades, se espera rechazo por falta de stock");
        rejected(InsufficientStockException.class, () -> service.reserve("ORDER-B", "PRE-1", 8));

        // Paso 7: pasa 1 milisegundo más y se cumplen las 24 horas.
        // El cliente A no pagó, así que sus 4 unidades se liberan.
        clock.advance(Duration.ofMillis(1));
        step("se cumplen 24 h sin pagar, hora " + clock.instant());
        verify("disponible (reserva liberada)", 10, service.available("PRE-1"));

        // Paso 8: ahora el cliente B sí puede reservar sus 8 unidades.
        show("reserva cliente B", service.reserve("ORDER-B", "PRE-1", 8));
        verify("disponible", 2, service.available("PRE-1"));

        // Paso 9: el cliente A intenta pagar tarde. Su reserva ya venció,
        // así que el pago se rechaza.
        step("cliente A intenta pagar tarde, se espera rechazo");
        rejected(IllegalStateException.class, () -> service.confirm("ORDER-A"));

        // Conclusión: resume lo que demostró el test.
        conclusion("la reserva PRE_ORDER aparto las unidades durante 24 h; al no pagar se liberaron,"
                + " otro cliente las reservo y el pago tardio fue rechazado");
    }

    @Test
    @DisplayName("PRE_ORDER: si paga dentro de las 24 horas, las unidades quedan vendidas")
    void preOrderPaidWithin24HoursStaysSold() {
        // Paso 1: el cliente A reserva 4 unidades. Quedan 6 disponibles.
        show("reserva cliente A", service.reserve("ORDER-A", "PRE-1", 4));
        verify("disponible", 6, service.available("PRE-1"));

        // Paso 2: pasan 23 horas. La transferencia llega antes del vencimiento.
        clock.advance(Duration.ofHours(23));
        step("pasan 23 h, llega la transferencia, hora " + clock.instant());

        // Paso 3: se confirma el pago. Las 4 unidades pasan de "apartadas" a "vendidas".
        // Si el pago fuera rechazado, confirm lanzaría una excepción y el test fallaría aquí.
        service.confirm("ORDER-A");
        step("pago de ORDER-A confirmado");
        verify("disponible tras pagar", 6, service.available("PRE-1"));

        // Paso 4: pasan 25 horas más (48 en total). Aunque ya pasó el vencimiento,
        // lo vendido no vuelve al almacén.
        clock.advance(Duration.ofHours(25));
        step("pasan 48 h desde la reserva, hora " + clock.instant());
        verify("disponible (lo vendido no vuelve)", 6, service.available("PRE-1"));

        // Conclusión: resume lo que demostró el test.
        conclusion("el pago dentro de las 24 h se acepto y las 4 unidades quedaron vendidas,"
                + " sin volver al almacen al pasar el vencimiento");
    }

    // Compara lo que devolvió el servicio con lo esperado y muestra ambos en consola.
    // Si no coinciden, el test falla en ese paso.
    private static void verify(String label, Object expected, Object actual) {
        System.out.println("   = " + label + ": obtenido " + actual + " | esperado " + expected
                + (expected.equals(actual) ? "  -> correcto" : "  -> NO COINCIDE"));
        assertEquals(expected, actual, label);
    }

    // Imprime la conclusión del test. Solo se llega aquí si todos los pasos anteriores pasaron.
    private static void conclusion(String text) {
        System.out.println("   >> Conclusion: " + text);
    }
}
