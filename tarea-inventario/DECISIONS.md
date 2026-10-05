# Decisiones

## Supuestos

**Pedidos**

- Cada pedido reserva un solo producto, como indica el contrato. Si llega el mismo `orderId` para otro producto, se rechaza con `IllegalStateException`.
- Si la app reenvía un pedido con la misma cantidad, se devuelve la misma reserva y no se reservan unidades de nuevo. Esto vale aunque ya no quede stock y aunque el pedido ya esté pagado.
- Si llega el mismo pedido con otra cantidad, se trata como un error de la app y se rechaza con `IllegalStateException`, en lugar de modificar la reserva.
- Si la reserva de un pedido venció, el mismo pedido puede volver a reservar el mismo producto.
- Un pedido rechazado por falta de stock o por superar el límite no queda registrado, así que puede reintentarse.

**Reglas de reserva**

- El límite por pedido de la categoría se valida antes que el stock. Un pedido de 3 unidades en `FLASH_SALE` siempre recibe `OrderLimitExceededException`, haya stock o no.
- Reservar un producto no registrado lanza `InsufficientStockException` con 0 disponibles, como dice el contrato.
- Registrar dos veces un producto con la misma categoría no hace nada y conserva su stock. Registrarlo con otra categoría lanza `IllegalArgumentException`.

**Vencimiento**

- La reserva vence en el instante exacto de `expiresAt` (creación + tiempo para pagar de la categoría). En ese instante las unidades ya están libres y la reserva no se puede confirmar.
- Las reservas vencidas se liberan cuando se vuelve a usar el producto (`reserve`, `confirm`, `available`, `addStock`), no con un proceso en segundo plano. Para quien consulta el resultado es el mismo: `available` nunca cuenta reservas vencidas.
- Confirmar una reserva descuenta las unidades del stock físico. Esas unidades ya no vuelven aunque pase el tiempo.

**Avisos de stock bajo**

- "Quedan 5 o menos" se mide sobre las unidades disponibles (stock menos reservas activas), no sobre el stock físico.
- El aviso se envía una sola vez y no se repite hasta que haya un reabastecimiento (`addStock`).
- Que se liberen reservas vencidas no cuenta como reabastecimiento.
- Si después de reabastecer el disponible sigue en 5 o menos, se envía un aviso nuevo.
- Confirmar un pago no genera aviso porque no cambia el disponible.
- Si falla el envío del aviso, la operación que lo generó igual se completa y el error queda en el log. Los avisos se envían después de liberar el bloqueo del producto, así un canal lento no frena otros pedidos.
- Para sumar canales futuros está `CompositeStockAlertListener`. Si un canal falla, los demás igual reciben el aviso.

**Categorías**

- Las reglas de cada categoría están en `CategoryPolicies`. Para agregar una categoría hay que sumarla al enum `ProductCategory` y darle su regla en el `switch`. El `switch` no tiene `default`, así que el proyecto no compila si una categoría queda sin reglas.

## Lo que dejé fuera

- **Persistencia.** Todo vive en memoria, como permite el enunciado. Si el servicio se reinicia, se pierden el stock y las reservas.
- **Limpieza de datos históricos.** Se guardan las reservas confirmadas y el vínculo de cada pedido con su producto, para responder bien a reenvíos atrasados. Nunca se borran, así que la memoria crece con cada pedido.
- **Cancelación explícita de una reserva.** El contrato no la contempla. Una reserva solo se libera cuando vence.
- **Reintento de avisos fallidos.** Si el canal falla, el aviso se registra en el log y se pierde.
- **Canales reales de aviso.** No implementé el envío por correo. El servicio recibe el canal desde afuera como `StockAlertListener`.
- **Observabilidad.** No hay métricas de reservas, vencimientos, rechazos ni avisos.

## Lo que cambiaría antes de llevarlo a producción

- **Base de datos y varias instancias.** El bloqueo actual es por producto y vive en la memoria de una sola instancia. Con varias instancias, la reserva tiene que ser atómica en la base de datos. Hay dos opciones: un `UPDATE` condicional que solo descuente si alcanza el disponible, o bloqueo de la fila del producto o control por versión.
- **Reenvíos con varias instancias.** El `orderId` tendría que ser una clave única en la base de datos. Así, dos reenvíos que caen en instancias distintas no reservan dos veces.
- **Una sola fuente de hora.** El vencimiento debería calcularse con la hora de la base de datos, o con relojes sincronizados, para que todas las instancias estén de acuerdo sobre qué reserva venció.
- **Avisos confiables.** El indicador de "aviso ya enviado" tendría que guardarse en la base de datos, junto con el cambio de stock. El envío pasaría por una cola de salida (outbox) con reintentos, para no perder avisos ni duplicarlos entre instancias.
- **Categorías configurables.** Marketing crea una categoría por temporada. Hoy eso exige cambiar el enum del contrato y volver a desplegar. Propondría que las reglas vengan de configuración o de la base de datos, lo que requiere acordar un cambio en el contrato.
- **Retención de datos.** Definir cuánto tiempo se guardan las reservas confirmadas y los pedidos, y limpiarlos periódicamente.
- **Contador de unidades reservadas.** Hoy el disponible se calcula sumando todas las reservas activas del producto en cada operación. Con mucho volumen convendría mantener un contador.
- **Umbral configurable.** El umbral de 5 unidades está fijo en `Inventory` y no se valida. Lo movería a configuración con validación.
- **Métricas y alertas operativas** sobre rechazos por falta de stock, reservas vencidas y avisos fallidos.
