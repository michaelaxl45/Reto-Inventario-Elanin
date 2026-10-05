package com.store.inventory.alert;

import static com.store.inventory.Trace.show;
import static com.store.inventory.Trace.step;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.store.inventory.api.StockAlertListener;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeStockAlertListenerTest {

    @Test
    void deliversToEveryChannel() {
        step("dos canales: correo y chat");
        List<String> email = new ArrayList<>();
        List<String> chat = new ArrayList<>();
        StockAlertListener listener = CompositeStockAlertListener.of(
                (sku, available) -> email.add(sku + ":" + available),
                (sku, available) -> chat.add(sku + ":" + available));

        listener.onLowStock("SKU-1", 3);

        assertEquals(List.of("SKU-1:3"), show("recibido por correo", email));
        assertEquals(List.of("SKU-1:3"), show("recibido por chat", chat));
    }

    @Test
    void failingChannelDoesNotStopTheOthers() {
        step("el correo falla, el chat funciona");
        List<String> chat = new ArrayList<>();
        StockAlertListener listener = CompositeStockAlertListener.of(
                (sku, available) -> {
                    throw new IllegalStateException("mail server down");
                },
                (sku, available) -> chat.add(sku + ":" + available));

        listener.onLowStock("SKU-1", 3);

        assertEquals(List.of("SKU-1:3"), show("recibido por chat", chat));
    }
}
