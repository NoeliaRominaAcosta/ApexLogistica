package com.fastdrop.warehouse.application.service;

import com.fastdrop.statemachine.api.InvalidStateTransitionException;
import com.fastdrop.statemachine.api.TransitionResult;
import com.fastdrop.warehouse.application.port.in.CancelReservationUseCase;
import com.fastdrop.warehouse.application.port.in.ConfirmReservationUseCase;
import com.fastdrop.warehouse.application.port.in.ReserveStockUseCase;
import com.fastdrop.warehouse.application.port.in.command.CancelReservationCommand;
import com.fastdrop.warehouse.application.port.in.command.ConfirmReservationCommand;
import com.fastdrop.warehouse.application.port.in.command.ReserveStockCommand;
import com.fastdrop.warehouse.application.port.out.DomainEventPublisherPort;
import com.fastdrop.warehouse.application.port.out.InventoryRepositoryPort;
import com.fastdrop.warehouse.application.port.out.ReservationContext;
import com.fastdrop.warehouse.application.port.out.ReservationRepositoryPort;
import com.fastdrop.warehouse.application.port.out.ReservationStateMachinePort;
import com.fastdrop.warehouse.domain.model.InventoryItem;
import com.fastdrop.warehouse.domain.model.ReservationEvent;
import com.fastdrop.warehouse.domain.model.ReservationState;
import com.fastdrop.warehouse.domain.model.StockReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService implements ReserveStockUseCase, ConfirmReservationUseCase, CancelReservationUseCase {

    public static final String HEADER_REASON = "reason";
    public static final String HEADER_TRACKING_NUMBER = "trackingNumber";

    private final ReservationRepositoryPort reservationRepository;
    private final InventoryRepositoryPort inventoryRepository;
    private final ReservationStateMachinePort stateMachine;
    private final DomainEventPublisherPort eventPublisher;

    @Override
    @Transactional
    public StockReservation reserve(ReserveStockCommand command) {
        log.info("Procesando reserva de stock para Orden={}", command.orderId());

        Optional<StockReservation> existing = reservationRepository.findByOrderId(command.orderId());
        if (existing.isPresent()) {
            log.warn("Idempotencia: la orden {} ya posee una reserva en estado {}",
                    command.orderId(), existing.get().getState());
            return existing.get();
        }

        String warehouseId = (command.warehouseId() != null && !command.warehouseId().isBlank())
                ? command.warehouseId()
                : "WH-CENTRAL-01";

        Optional<InventoryItem> inventoryOpt = inventoryRepository.findBySkuAndWarehouseId(command.sku(), warehouseId)
                .or(() -> inventoryRepository.findFirstBySku(command.sku()));

        InventoryItem inventoryItem = inventoryOpt.orElse(null);

        StockReservation reservation = StockReservation.createNew(
                command.orderId(),
                inventoryItem != null ? inventoryItem.getWarehouseId() : warehouseId,
                command.sku(),
                command.quantity(),
                command.deliveryAddress()
        );

        String rejectionReason = inventoryItem == null
                ? "No se encontró inventario para SKU: " + command.sku()
                : String.format("Stock insuficiente para SKU %s. Disponible: %d, Solicitado: %d",
                        command.sku(), inventoryItem.getAvailableStock(), command.quantity());

        TransitionResult<ReservationState> result = stateMachine.fire(
                new ReservationContext(reservation, inventoryItem),
                ReservationEvent.RESERVE,
                Map.of(HEADER_REASON, rejectionReason)
        );

        if (!result.accepted()) {
            throw new InvalidStateTransitionException(command.orderId(), result.source(), ReservationEvent.RESERVE);
        }

        reservation.changeState(result.target());
        if (inventoryItem != null && result.target() == ReservationState.RESERVED) {
            inventoryRepository.save(inventoryItem);
        }
        StockReservation saved = reservationRepository.save(reservation);
        eventPublisher.publish(reservation.pullDomainEvents());

        log.info("Reserva procesada para Orden={} con estado final={}", command.orderId(), saved.getState());
        return saved;
    }

    @Override
    @Transactional
    public StockReservation confirm(ConfirmReservationCommand command) {
        log.info("Confirmando reserva de stock para Orden={}, Tracking={}", command.orderId(), command.trackingNumber());

        Optional<StockReservation> reservationOpt = reservationRepository.findByOrderId(command.orderId());
        if (reservationOpt.isEmpty()) {
            log.warn("Confirmación ignorada: no existe reserva para la orden {}", command.orderId());
            return null;
        }

        StockReservation reservation = reservationOpt.get();
        if (reservation.getState() == ReservationState.CONFIRMED) {
            log.info("La reserva para la orden {} ya estaba confirmada (Idempotencia)", command.orderId());
            return reservation;
        }

        TransitionResult<ReservationState> result = stateMachine.fire(
                new ReservationContext(reservation, null),
                ReservationEvent.CONFIRM,
                Map.of(HEADER_TRACKING_NUMBER, command.trackingNumber() != null ? command.trackingNumber() : "")
        );

        if (!result.accepted()) {
            if (reservation.getState().isTerminal()) {
                log.info("CONFIRM ignorado: la reserva {} ya se encuentra en estado terminal {}",
                        command.orderId(), reservation.getState());
                return reservation;
            }
            throw new InvalidStateTransitionException(command.orderId(), result.source(), ReservationEvent.CONFIRM);
        }

        reservation.changeState(result.target());
        StockReservation saved = reservationRepository.save(reservation);
        eventPublisher.publish(reservation.pullDomainEvents());
        return saved;
    }

    @Override
    @Transactional
    public StockReservation cancel(CancelReservationCommand command) {
        String reason = command.reason() != null ? command.reason() : "Cancelación solicitada";
        log.warn("Ejecutando cancelación/compensación de reserva para Orden={}, Motivo={}", command.orderId(), reason);

        Optional<StockReservation> reservationOpt = reservationRepository.findByOrderId(command.orderId());
        if (reservationOpt.isEmpty()) {
            // Tombstone: si la compensación llega antes que ReserveStock, registramos la reserva cancelada
            log.warn("No existe reserva previa para orden {}. Creando tombstone CANCELLED.", command.orderId());
            StockReservation tombstone = StockReservation.createCancelledTombstone(command.orderId(), reason);
            StockReservation saved = reservationRepository.save(tombstone);
            eventPublisher.publish(tombstone.pullDomainEvents());
            return saved;
        }

        StockReservation reservation = reservationOpt.get();
        if (reservation.getState().isTerminal()) {
            log.info("La reserva para la orden {} ya se encuentra en estado terminal {} (Idempotencia)",
                    command.orderId(), reservation.getState());
            return reservation;
        }

        InventoryItem inventoryItem = inventoryRepository
                .findBySkuAndWarehouseId(reservation.getSku(), reservation.getWarehouseId())
                .orElse(null);

        TransitionResult<ReservationState> result = stateMachine.fire(
                new ReservationContext(reservation, inventoryItem),
                ReservationEvent.CANCEL,
                Map.of(HEADER_REASON, reason)
        );

        if (!result.accepted()) {
            throw new InvalidStateTransitionException(command.orderId(), result.source(), ReservationEvent.CANCEL);
        }

        reservation.changeState(result.target());
        if (inventoryItem != null) {
            inventoryRepository.save(inventoryItem);
        }
        StockReservation saved = reservationRepository.save(reservation);
        eventPublisher.publish(reservation.pullDomainEvents());

        log.info("Cancelación completada y Outbox registrado para orden {}", command.orderId());
        return saved;
    }
}
