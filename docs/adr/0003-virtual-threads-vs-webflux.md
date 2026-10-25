# ADR-003: Uso de Virtual Threads (Java 21+) frente a Programación Reactiva (WebFlux)

* **Estado:** Aceptado
* **Fecha:** 2026-10-14
* **Decisores:** Equipo de Arquitectura FastDrop
* **Contexto Técnico:** Java 21, Spring Boot 3.3+, Spring MVC con Tomcat embebido

---

## 1. Contexto

FastDrop cuenta con endpoints de alta concurrencia y operaciones bloqueantes por I/O:
* Consultas frecuentes al endpoint de tracking (`GET /api/shipments/{id}/tracking`).
* Consultas a bases de datos relacionales PostgreSQL mediante Spring Data JPA / JDBC.
* Polling en workers de Outbox y llamadas HTTP a proveedores externos de cartografía.

Se requería un modelo de concurrencia capaz de soportar decenas de miles de solicitudes simultáneas sin incurrir en agotamiento de hilos del sistema operativo (OS Threads).

---

## 2. Opciones Consideradas

### Opción A: Programación Reactiva (Spring WebFlux + R2DBC + Project Reactor)
* *Pros:* Alto throughput con pocos hilos y nula contención de OS threads.
* *Contras:* 
  * "Coloración" de funciones (`Mono<T>`, `Flux<T>`).
  * Pérdida de ergonomía en debugging, stack traces complejos e ilegibles.
  * Incompatibilidad con el ecosistema JDBC maduro (JPA, Hibernate, Flyway).
  * Dificultades para propagar `ThreadLocal` (requiere Context Propagation explícito en cada operador reactivo).

### Opción B: Virtual Threads (Project Loom en Java 21) + Spring MVC clásico
* *Pros:*
  * Mantiene el modelo de programación secuencial e intuitivo (*thread-per-request*).
  * Compatible al 100% con JPA, JDBC, Hibernate y la rica variedad de librerías Java existentes.
  * Los virtual threads son administrados en el heap de la JVM, son extremadamente livianos (~1KB por hilo) y se desmontan automáticamente de los hilos carrier cuando se bloquean en I/O.
  * Habilitable con una única propiedad: `spring.threads.virtual.enabled=true`.
  * Stack traces y profiling nativo estándar con herramientas habituales (JFR, Async Profiler).
* *Contras:* Debe evitarse el *pinning* de hilos en bloques `synchronized` muy antiguos (resuelto en JDK 23+ y con drivers modernos como pgjdbc).

---

## 3. Decisión

Se adopta **Virtual Threads con Spring MVC (Opción B)** para todos los microservicios de la plataforma FastDrop.

Se configura en `application.yml`:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

---

## 4. Resultados Obtenidos
* El endpoint de tracking es capaz de escalar horizontalmente y manejar picos de concurrencia de lectura sin saturar el thread pool de Tomcat.
* Se simplificó drásticamente el código de negocio, manteniendo legibilidad, trazabilidad distribuida e instrumentación estándar con Micrometer y OpenTelemetry.
