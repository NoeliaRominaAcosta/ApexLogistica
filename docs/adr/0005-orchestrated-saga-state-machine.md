# ADR-005: Evolución a Saga Orquestada con Máquina de Estados en dos Niveles

* **Estado:** Aceptado (Reemplaza a ADR-002)
* **Fecha:** 2026-10-25
* **Decisores:** Equipo de Arquitectura FastDrop
* **Contexto Técnico:** Apache Kafka, Spring State Machine 4.0.0, Transactional Outbox

---

## 1. Contexto

En [ADR-002](file:///C:/Users/Noxie-PC/Documents/logistica/docs/adr/0002-saga-coordination.md) se definió que si el flujo de fulfillment superaba los 5–6 pasos con ramificaciones de compensación, se incorporaría un orquestador liviano con Spring State Machine.

Al incorporar retiro por conductor (`PICK_UP`), entrega en domicilio (`DELIVER`), fallo de entrega en tránsito (`DELIVERY_FAILED` con devolución de stock al warehouse), cancelación manual en cualquier etapa previa al retiro y control de timeouts, el proceso alcanzó **10 estados y múltiples rutas compensatorias**.

## 2. Decisión

Se adopta un modelo de **Saga Orquestada con máquinas de estado en dos niveles**, ambos construidos sobre `common-statemachine`:

1. **Nivel Proceso (`order-orchestrator-service` en `:8084`):**
   - Gestiona el agregado `OrderSaga` y su máquina de estados (`NEW`, `RESERVING_STOCK`, `SCHEDULING_DELIVERY`, `AWAITING_PICKUP`, `IN_TRANSIT`, `CANCELLING_SHIPMENT`, `RELEASING_STOCK`, `COMPLETED`, `REJECTED`, `CANCELLED`).
   - Emite comandos explícitos por Outbox hacia `warehouse.commands` y `dispatch.commands`.
   - Consume las respuestas de los participantes desde `warehouse.inventory-events` y `logistics.dispatch-events` con deduplicación en `processed_messages`.
   - Gestiona expiración de pasos mediante una columna `deadline` y `SagaTimeoutScheduler` (`FOR UPDATE SKIP LOCKED`).
2. **Nivel Entidad (`warehouse-service` y `dispatch-service`):**
   - Cada microservicio mantiene su propia máquina de estados local (`StockReservation` y `Shipment`) para proteger invariantes de su dominio.
   - Si una entrega falla en calle (`IN_TRANSIT -> FAILED`), el orquestador transiciona a `RELEASING_STOCK` y envía `ReleaseStockCommand` al warehouse, donde la transición `CONFIRMED --CANCEL--> CANCELLED` devuelve el stock físico al inventario disponible.
