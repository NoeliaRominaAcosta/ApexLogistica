# FastDrop Logistics Platform 🚚📦

Plataforma distribuida de logística y fulfillment de envíos construida con **Java 21/23**, **Spring Boot 3.3.4**, **PostgreSQL 16**, **Apache Kafka (KRaft)**, **Redis 7**, **Resilience4j**, **Virtual Threads** y **OpenTelemetry / Zipkin**.

---

## 🏛️ Arquitectura del Sistema

El sistema implementa tres microservicios desacoplados según los bounded contexts de negocio:

1. **Warehouse Service (`:8081`):**
   - Gestión de stock en centros de distribución.
   - Control de concurrencia optimista (`@Version` en JPA) para evitar sobreventas bajo alta demanda.
   - **Transactional Outbox Pattern**: Escritura atómica de inventario y evento en `outbox_events` con `SELECT ... FOR UPDATE SKIP LOCKED`.
   - **Saga Step 1 & Compensación**: Al recibir `DeliveryFailed`, revierte la reserva física devolviendo stock al inventario (`StockCancelled`).

2. **Dispatch Service (`:8082`):**
   - Coordinación de rutas y asignación de conductores.
   - Integración con API externa protegida por **Resilience4j**:
     - **Circuit Breaker** (con ventana deslizante y umbral de fallos al 50%).
     - **Retry con Backoff Exponencial**.
     - **Fallback**: emite `DeliveryFailed` si no hay flota disponible para activar la compensación en Warehouse.
   - Transactional Outbox para publicación en Kafka.

3. **Tracking Service (`:8083`):**
   - Consultas de tracking de envíos de alta concurrencia impulsadas por **Virtual Threads** (`spring.threads.virtual.enabled=true`).
   - **Cache-Aside Pattern** con Redis (TTL de 30s).
   - **Invalidación reactiva**: Escucha eventos de Kafka (`StockReserved`, `DeliveryScheduled`, `DeliveryFailed`, `StockCancelled`) y purga proactivamente las claves de Redis.
   - **Rate Limiting** con Resilience4j (50 req/s con ventana de rechazo HTTP 429).

---

## 📂 Estructura del Proyecto

```
logistica/
├── common-events/            # Eventos de dominio, DTOs compartidos y utilitarios W3C traceparent
├── warehouse-service/        # Inventario + Optimistic Locking + Outbox + Compensación Saga
├── dispatch-service/         # Despacho + Resilience4j (Circuit Breaker / Retry) + Outbox
├── tracking-service/         # Redis Cache-Aside + Virtual Threads + Rate Limiter
├── docker/
│   └── init-databases.sql    # Inicialización de warehouse_db y dispatch_db en PostgreSQL
├── docker-compose.yml        # Infraestructura (Postgres 16, Kafka KRaft, Redis 7, Zipkin)
├── docs/
│   ├── adr/                  # Architecture Decision Records (ADR-001, ADR-002, ADR-003)
│   └── c4-model/             # Diagramas C4 en PlantUML (Context, Containers, Components)
└── k8s/base/                 # Manifiestos de Kubernetes con Probes (Liveness/Readiness/Startup)
```

---

## 🚀 Puesta en Marcha

### 1. Iniciar Infraestructura
```bash
docker compose up -d
```
Esto levantará:
- **PostgreSQL 16** en `localhost:5432` con `warehouse_db` y `dispatch_db`.
- **Kafka (KRaft)** en `localhost:9092`.
- **Redis 7** en `localhost:6379`.
- **Zipkin** en `http://localhost:9411` para trazabilidad distribuida.

### 2. Compilar y Ejecutar Pruebas
```bash
mvn clean test
```

### 3. Ejecutar los Microservicios
```bash
# Terminal 1: Warehouse Service
mvn -pl warehouse-service spring-boot:run

# Terminal 2: Dispatch Service
mvn -pl dispatch-service spring-boot:run

# Terminal 3: Tracking Service
mvn -pl tracking-service spring-boot:run
```

---

## 🔄 Flujo Saga: Escenario Exitoso y Compensatorio

### Escenario 1: Flujo Exitoso
1. **Inicializar Stock:**
   ```bash
   curl -X POST http://localhost:8081/api/inventory/init \
     -H "Content-Type: application/json" \
     -d '{"sku": "LAPTOP-01", "totalStock": 10}'
   ```

2. **Reservar Stock (Inicia Saga):**
   ```bash
   curl -X POST http://localhost:8081/api/orders/reserve \
     -H "Content-Type: application/json" \
     -d '{"orderId": "ORD-100", "sku": "LAPTOP-01", "quantity": 1, "deliveryAddress": "Av. Corrientes 1234, CABA"}'
   ```

3. **Consultar Tracking (Cache-Aside):**
   ```bash
   curl http://localhost:8083/api/shipments/ORD-100/tracking
   ```
   *Respuesta esperada:* Estado `DELIVERY_SCHEDULED` con conductor asignado y guía generada.

### Escenario 2: Compensación y Rollback (Falla en Asignación)
1. **Activar Simulación de Fallo en Despacho:**
   ```bash
   curl -X POST http://localhost:8082/api/shipments/simulate-fail?enabled=true
   ```

2. **Crear Orden:**
   ```bash
   curl -X POST http://localhost:8081/api/orders/reserve \
     -H "Content-Type: application/json" \
     -d '{"orderId": "ORD-200", "sku": "LAPTOP-01", "quantity": 1, "deliveryAddress": "Zona Sin Cobertura"}'
   ```

3. **Resultado:**
   - `dispatch-service` detecta falla, activa el **Fallback de Resilience4j** y emite `DeliveryFailed`.
   - `warehouse-service` consume `DeliveryFailed`, ejecuta rollback automático devolviendo el stock reservado al inventario disponible y emite `StockCancelled`.
   - `tracking-service` invalida cache e informa estado `CANCELLED`.
