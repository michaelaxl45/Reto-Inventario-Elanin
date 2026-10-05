package com.store.inventory;

import static com.store.inventory.Trace.rejected;
import static com.store.inventory.Trace.show;
import static com.store.inventory.Trace.step;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReservationRulesTest {

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = Inventory.create(Clock.systemUTC(), (sku, available) -> { });
    }

    @Test
    void flashSaleAllowsUpToTwoUnitsPerOrder() {
        step("FLASH-1 FLASH_SALE con 100 unidades");
        service.registerProduct("FLASH-1", ProductCategory.FLASH_SALE);
        service.addStock("FLASH-1", 100);

        show("reserva ORDER-1 (2 u., el maximo)", service.reserve("ORDER-1", "FLASH-1", 2));

        assertEquals(98, show("disponible", service.available("FLASH-1")));
    }

    @Test
    void flashSaleRejectsMoreThanTwoUnitsPerOrder() {
        step("FLASH-1 FLASH_SALE con 100 unidades, ORDER-1 pide 3");
        service.registerProduct("FLASH-1", ProductCategory.FLASH_SALE);
        service.addStock("FLASH-1", 100);

        rejected(OrderLimitExceededException.class, () -> service.reserve("ORDER-1", "FLASH-1", 3));
        assertEquals(100, show("disponible (sin cambios)", service.available("FLASH-1")));
    }

    @Test
    void orderLimitIsCheckedBeforeStock() {
        step("FLASH-1 FLASH_SALE con 1 unidad, ORDER-1 pide 3 (excede limite y stock)");
        service.registerProduct("FLASH-1", ProductCategory.FLASH_SALE);
        service.addStock("FLASH-1", 1);

        rejected(OrderLimitExceededException.class, () -> service.reserve("ORDER-1", "FLASH-1", 3));
    }

    @ParameterizedTest
    @EnumSource(value = ProductCategory.class, names = {"STANDARD", "PRE_ORDER"})
    void categoriesWithoutLimitAcceptLargeOrders(ProductCategory category) {
        step("SKU-1 " + category + " con 1000 unidades");
        service.registerProduct("SKU-1", category);
        service.addStock("SKU-1", 1_000);

        show("reserva ORDER-1 (1000 u.)", service.reserve("ORDER-1", "SKU-1", 1_000));

        assertEquals(0, show("disponible", service.available("SKU-1")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void reserveRejectsNonPositiveQuantity(int quantity) {
        step("SKU-1 con 10 unidades, ORDER-1 pide " + quantity);
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);

        rejected(IllegalArgumentException.class, () -> service.reserve("ORDER-1", "SKU-1", quantity));
    }

    @Test
    void unknownProductHasNoStock() {
        step("UNKNOWN nunca fue registrado");
        assertEquals(0, show("disponible", service.available("UNKNOWN")));
        rejected(InsufficientStockException.class, () -> service.reserve("ORDER-1", "UNKNOWN", 1));
    }

    @Test
    void registeredProductStartsWithoutStock() {
        step("SKU-1 registrado sin agregar stock");
        service.registerProduct("SKU-1", ProductCategory.STANDARD);

        assertEquals(0, show("disponible", service.available("SKU-1")));
        rejected(InsufficientStockException.class, () -> service.reserve("ORDER-1", "SKU-1", 1));
    }

    @Test
    void reservationsOfDifferentOrdersAddUp() {
        //Registra un producto con código SKU-1 y categoría STANDARD (15 min para pagar, sin límite por pedido).
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        ///Ingresan 10 unidades al almacén Stock: 10, reservado: 0, disponible: 10.
        service.addStock("SKU-1", 10);
        show("disponible inicial", service.available("SKU-1"));
        ///El cliente A (pedido ORDER-1) aparta 3. Stock: 10, reservado: 3, disponible: 7.
        show("reserva cliente A", service.reserve("ORDER-1", "SKU-1", 3));
        show("disponible", service.available("SKU-1"));
        show("reserva cliente B", service.reserve("ORDER-2", "SKU-1", 5));

        assertEquals(2, show("disponible", service.available("SKU-1")));
        step("cliente C pide 3");
        rejected(InsufficientStockException.class, () -> service.reserve("ORDER-3", "SKU-1", 3));
    }

    @Test
    void addStockAccumulates() {
        step("SKU-1: entran 10 y luego 5 unidades");
        service.registerProduct("SKU-1", ProductCategory.STANDARD);

        service.addStock("SKU-1", 10);
        service.addStock("SKU-1", 5);

        assertEquals(15, show("disponible", service.available("SKU-1")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3})
    void addStockRejectsNonPositiveQuantity(int quantity) {
        step("SKU-1: se intenta agregar " + quantity + " unidades");
        service.registerProduct("SKU-1", ProductCategory.STANDARD);

        rejected(IllegalArgumentException.class, () -> service.addStock("SKU-1", quantity));
    }

    @Test
    void addStockRejectsUnregisteredProduct() {
        step("se intenta agregar stock a UNKNOWN (no registrado)");
        rejected(IllegalArgumentException.class, () -> service.addStock("UNKNOWN", 10));
    }

    @Test
    void registeringTheSameProductTwiceKeepsItsStock() {
        step("SKU-1 con 10 unidades se registra de nuevo con la misma categoria");
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);

        service.registerProduct("SKU-1", ProductCategory.STANDARD);

        assertEquals(10, show("disponible", service.available("SKU-1")));
    }

    @Test
    void productCannotChangeItsCategory() {
        step("SKU-1 STANDARD se intenta registrar como FLASH_SALE");
        service.registerProduct("SKU-1", ProductCategory.STANDARD);

        rejected(IllegalArgumentException.class,
                () -> service.registerProduct("SKU-1", ProductCategory.FLASH_SALE));
    }
}
