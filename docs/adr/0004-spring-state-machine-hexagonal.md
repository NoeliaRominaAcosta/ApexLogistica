# ADR-004: Adopción de Arquitectura Hexagonal y Spring State Machine Aislada Detrás de Puertos

* **Estado:** Aceptado
* **Fecha:** 2026-10-25
* **Decisores:** Equipo de Arquitectura FastDrop
* **Contexto Técnico:** Java 21, Spring Boot 3.3.4, Spring Statemachine 4.0.0, ArchUnit 1.3.0

---

## 1. Contexto

Los agregados principales (`StockReservation`, `Shipment` y `OrderSaga`) atravesaban cambios de estado mediante validaciones condicionales dispersas en los servicios de aplicación y acopladas con entidades JPA, repositorios y clientes de Kafka.

Se decidió incorporar **Spring State Machine (SSM)** para formalizar las transiciones, guards y actions de cada ciclo de vida, junto con una reorganización hacia **Arquitectura Hexagonal (Ports & Adapters)**.

## 2. Riesgo Identificado sobre Spring Statemachine

`Spring Statemachine 4.0.x` es la última línea mantenida como código abierto (el repositorio fue archivado en `spring-attic` y el mantenimiento posterior pasó a modalidad comercial Tanzu). Aunque es plenamente compatible con Spring Boot 3.3.x y Jakarta EE, no cuenta con soporte OSS para futuras versiones mayores (como Spring Boot 4).

## 3. Decisión

1. **Aislamiento Hexagonal:**
   - La capa `domain` contiene modelos puros (`StockReservation`, `Shipment`, `OrderSaga`), enums de estado/evento y eventos de dominio sin dependencias de Spring ni de JPA.
   - La capa `application` define casos de uso (`port.in`) y puertos de salida (`port.out`), comunicándose con la máquina de estados únicamente a través de la interfaz agnóstica `StateMachinePort<C, S, E>`.
   - La implementación con Spring State Machine vive exclusivamente en `infrastructure.adapter.out.statemachine`.
2. **Módulo Compartido `common-statemachine`:**
   - Paquete `com.fastdrop.statemachine.api` (Java puro): `TransitionSubject`, `StateMachinePort`, `TransitionResult` y excepciones de transición.
   - Paquete `com.fastdrop.statemachine.ssm`: `AbstractStateMachineAdapter` (rehidratación desde el estado persistido del agregado, ejecución síncrona en la misma transacción y propagación de excepciones ocurridas en las actions), `ContextAwareGuard`, `ContextAwareAction` y `MetricsStateMachineListener`.
3. **Verificación Automatizada con ArchUnit:**
   - Cada servicio incluye tests de arquitectura (`*HexagonalArchitectureTest`) que impiden que `domain` o `application` importen clases de `org.springframework.statemachine..`, `jakarta.persistence..` o `..infrastructure..`.
