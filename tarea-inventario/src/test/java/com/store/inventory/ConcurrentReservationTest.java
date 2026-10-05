package com.store.inventory;

import static com.store.inventory.Trace.show;
import static com.store.inventory.Trace.step;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class ConcurrentReservationTest {

    private static final int CUSTOMERS = 500;

    private RecordingAlertListener listener;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        listener = new RecordingAlertListener();
        service = Inventory.create(Clock.systemUTC(), listener);
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
    }

    @RepeatedTest(5)
    void neverSellsMoreUnitsThanInStock() throws Exception {
        step("SKU-1 con 100 unidades, " + CUSTOMERS + " clientes reservan 1 al mismo tiempo");
        service.addStock("SKU-1", 100);
        AtomicInteger reserved = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        runConcurrently(CUSTOMERS, customer -> {
            try {
                service.reserve("ORDER-" + customer, "SKU-1", 1);
                reserved.incrementAndGet();
            } catch (InsufficientStockException e) {
                rejected.incrementAndGet();
            }
        });

        assertEquals(100, show("reservas exitosas", reserved.get()));
        assertEquals(CUSTOMERS - 100, show("rechazadas por falta de stock", rejected.get()));
        assertEquals(0, show("disponible", service.available("SKU-1")));
        assertEquals(1, show("avisos", listener.alerts()).size());
    }

    @Test
    void sameOrderResentConcurrentlyIsReservedOnce() throws Exception {
        step("SKU-1 con 100 unidades, ORDER-1 (3 u.) llega " + CUSTOMERS + " veces al mismo tiempo");
        service.addStock("SKU-1", 100);

        runConcurrently(CUSTOMERS, attempt -> service.reserve("ORDER-1", "SKU-1", 3));

        assertEquals(97, show("disponible (se reservo una sola vez)", service.available("SKU-1")));
    }

    @Test
    void concurrentConfirmationsSellEachReservationOnce() throws Exception {
        step("SKU-1 con 100 unidades, 50 pedidos de 2 u., " + CUSTOMERS + " confirmaciones al mismo tiempo");
        service.addStock("SKU-1", 100);
        for (int i = 0; i < 50; i++) {
            service.reserve("ORDER-" + i, "SKU-1", 2);
        }
        AtomicInteger confirmed = new AtomicInteger();

        runConcurrently(CUSTOMERS, attempt -> {
            try {
                service.confirm("ORDER-" + (attempt % 50));
                confirmed.incrementAndGet();
            } catch (IllegalStateException e) {
                // already confirmed by another attempt
            }
        });

        assertEquals(50, show("confirmaciones exitosas", confirmed.get()));
        assertEquals(0, show("disponible", service.available("SKU-1")));
    }

    private interface Task {
        void run(int index) throws Exception;
    }

    private static void runConcurrently(int tasks, Task task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Void>> callables = new ArrayList<>();
        for (int i = 0; i < tasks; i++) {
            int index = i;
            callables.add(() -> {
                start.await();
                task.run(index);
                return null;
            });
        }
        try (ExecutorService executor = Executors.newFixedThreadPool(32)) {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> callable : callables) {
                futures.add(executor.submit(callable));
            }
            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        }
    }
}
