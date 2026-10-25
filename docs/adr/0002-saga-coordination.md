# ADR-002: Elección de Saga Coreografiada vs Orquestada para el Flujo de Fulfillment

* **Estado:** Reemplazado por [ADR-005](0005-orchestrated-saga-state-machine.md)
* **Fecha:** 2026-10-14
* **Decisores:** Equipo de Arquitectura FastDrop
* **Contexto Técnico:** Apache Kafka, Spring Boot 3, Patrones Distribuidos

---

## 1. Contexto

El flujo de entrega de una orden en FastDrop involucra tres dominios independientes:
1. **Warehouse Service:** Reserva stock físico.
2. **Dispatch Service:** Asigna transportista, optimiza ruta y emite guía.
3. **Tracking Service:** Publica estados y checkpoints a los usuarios.

Si la asignación de transporte falla (ej. sin conductores disponibles o API de mapas caída), el sistema debe ejecutar una transacción compensatoria para cancelar la reserva de stock y notificar la cancelación de la orden.

---

## 2. Opciones Consideradas

### Opción A: Saga Orquestada (Orchestration-based Saga)
Un coordinador central (ej. Camunda, Temporal o un servicio orquestador dedicado) envía comandos explícitos a cada microservicio y maneja la máquina de estados.
* *Pros:* Flujo centralizado y fácil de visualizar en un único punto.
* *Contras:* Introduce un punto único de falla, acoplamiento con el orquestador y complejidad operativa innecesaria para un flujo lineal de 2-3 pasos.

### Opción B: Saga Coreografiada (Choreography-based Saga)
Los servicios publican y reaccionan a eventos de dominio de forma descentralizada a través de tópicos de Apache Kafka:
* Éxito: `OrderPlaced` $\rightarrow$ `StockReserved` $\rightarrow$ `DeliveryScheduled`.
* Compensación: `DeliveryFailed` $\rightarrow$ `CancelStockReservation` / `StockCancelled`.
* *Pros:* Desacoplamiento total, alta resiliencia, extensibilidad natural para nuevos consumidores (como analítica o tracking).
* *Contras:* La lógica del flujo está distribuida y requiere buena instrumentación de observabilidad distribuida.

---

## 3. Decisión

Se adopta la **Saga Coreografiada (Opción B)** con soporte para comandos compensatorios explícitos (`orders.saga-commands` y `logistics.dispatch-events`).

Para mitigar la desventaja de observabilidad:
* Se estandariza el encabezado W3C `traceparent` a través de Kafka headers.
* Cada evento incluye `orderId` como clave de partición en Kafka para preservar el orden temporal estricto.
* Las compensaciones son idempotentes: si `WarehouseService` recibe múltiples eventos de cancelación para la misma orden, verifica el estado previo antes de aplicar la devolución.

---

## 4. Estado y Evolución
Si en el futuro el flujo de fulfillment supera los 5-6 pasos y ramificaciones complejas de decisión, se evaluará la incorporación de un orquestador liviano con Temporal o Spring State Machine.
