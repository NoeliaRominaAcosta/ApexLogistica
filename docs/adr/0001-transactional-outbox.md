# ADR-001: Implementación del Patrón Transactional Outbox para Publicación Confiable de Eventos

* **Estado:** Aceptado
* **Fecha:** 2026-10-14
* **Decisores:** Equipo de Arquitectura e Ingeniería Backend FastDrop
* **Contexto Técnico:** Java 21, Spring Boot 3.x, PostgreSQL 16, Apache Kafka

---

## 1. Contexto y Planteamiento del Problema

En el sistema de logística y fulfillment de envíos (**FastDrop**), los microservicios (`Warehouse`, `Dispatch`, `Tracking`) necesitan persistir cambios de estado en sus bases de datos relacionales y notificar de inmediato a otros microservicios mediante eventos de dominio en Apache Kafka (por ejemplo, `StockReserved`, `DeliveryScheduled`).

El enfoque ingenuo de *Dual-Write* (escribir en la base de datos y luego invocar `kafkaTemplate.send()`) introduce fallos de atomicidad críticos:
1. Si la transacción en PostgreSQL hace commit exitoso pero el broker de Kafka no está disponible o falla la red, el evento se pierde definitivamente, dejando al resto del sistema en un estado inconsistente.
2. Si se invoca a Kafka primero y la transacción en PostgreSQL falla (rollback por violación de constraints, timeout, etc.), se emite un evento fantasma al clúster para un cambio que nunca existió.

El uso de transacciones distribuidas tradicionales tipo Two-Phase Commit (2PC / XA) no es viable: introduce bloqueos prolongados, reduce drásticamente el throughput, agrega un punto único de falla con el coordinador de transacciones y no cuenta con soporte nativo eficiente en brokers modernos como Kafka.

---

## 2. Decisiones Consideradas

* **Opción 1: Dual-Write con reintentos locales en memoria.** (Rechazado: pérdida ante caídas de proceso).
* **Opción 2: Two-Phase Commit (2PC) / XA Transactions.** (Rechazado: alto acoplamiento, latencia y bloqueos).
* **Opción 3: Transactional Outbox Pattern con relay asíncrono (Polling Publisher con SKIP LOCKED).** (Seleccionado).
* **Opción 4: Transactional Outbox Pattern con Change Data Capture (CDC / Debezium).** (Fase evolutiva posterior).

---

## 3. Decisión Aceptada

Se decide implementar el **Patrón Transactional Outbox (Opción 3 con evolución planeada a Opción 4)**:

1. **Escritura Atómica Local:** Cada servicio persistirá la entidad de negocio y el registro del evento en una tabla dedicada (`outbox_events`) dentro de la **misma transacción local de PostgreSQL** (`@Transactional`).
2. **Esquema de Outbox:**
   * `id` (UUID, PK)
   * `aggregate_type` (VARCHAR)
   * `aggregate_id` (VARCHAR)
   * `event_type` (VARCHAR)
   * `payload` (TEXT/JSON)
   * `trace_context` (W3C `traceparent` header para trazabilidad distribuida)
   * `created_at` (TIMESTAMPTZ)
   * `processed_at` (TIMESTAMPTZ, nullable)
3. **Mecanismo de Despacho:** Un worker en segundo plano (`@Scheduled` aprovechando Virtual Threads de Java 21) lee eventos pendientes usando la sentencia SQL:
   ```sql
   SELECT * FROM outbox_events
   WHERE processed_at IS NULL
   ORDER BY created_at ASC
   LIMIT 50
   FOR UPDATE SKIP LOCKED;
   ```
4. **Propagación de Trazabilidad:** El worker inyecta el `traceparent` original en los Kafka Headers, garantizando trazabilidad de punta a punta entre productor y consumidor.

---

## 4. Consecuencias y Mitigaciones

* **Entrega At-Least-Once:** Los eventos pueden entregarse más de una vez ante reinicios de red. 
  * *Mitigación:* Todos los consumidores de la Saga y del Tracking implementan idempotencia mediante verificación previa de estado y claves de agregados.
* **Sobrecarga en la Base de Datos:**
  * *Mitigación:* Se crean índices compuestos en `(processed_at, created_at)` y se utiliza `FOR UPDATE SKIP LOCKED` para permitir múltiples instancias del worker sin contención de bloqueos.
