package com.fastdrop.orchestrator.infrastructure.adapter.out.statemachine;

import com.fastdrop.orchestrator.application.port.out.SagaContext;
import com.fastdrop.orchestrator.application.service.OrderSagaService;
import com.fastdrop.orchestrator.domain.model.SagaEvent;
import com.fastdrop.orchestrator.domain.model.SagaState;
import com.fastdrop.statemachine.ssm.ContextAwareAction;
import com.fastdrop.statemachine.ssm.MetricsStateMachineListener;
import com.fastdrop.statemachine.ssm.StateContexts;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory(name = "sagaStateMachineFactory")
public class SagaStateMachineConfig extends EnumStateMachineConfigurerAdapter<SagaState, SagaEvent> {

    private final MeterRegistry meterRegistry;

    public SagaStateMachineConfig(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<SagaState, SagaEvent> config) throws Exception {
        config.withConfiguration()
                .autoStartup(false)
                .listener(new MetricsStateMachineListener<>("order-saga", meterRegistry));
    }

    @Override
    public void configure(StateMachineStateConfigurer<SagaState, SagaEvent> states) throws Exception {
        states.withStates()
                .initial(SagaState.NEW)
                .states(EnumSet.allOf(SagaState.class))
                .end(SagaState.COMPLETED)
                .end(SagaState.REJECTED)
                .end(SagaState.CANCELLED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<SagaState, SagaEvent> transitions) throws Exception {
        var reserveStockAction = new ContextAwareAction<SagaState, SagaEvent, SagaContext>() {
            @Override
            protected void run(SagaContext c, StateContext<SagaState, SagaEvent> ctx) {
                c.saga().requestReserveStock();
            }
        };

        var scheduleDeliveryAction = new ContextAwareAction<SagaState, SagaEvent, SagaContext>() {
            @Override
            protected void run(SagaContext c, StateContext<SagaState, SagaEvent> ctx) {
                String warehouseId = StateContexts.headerOrDefault(
                        ctx, OrderSagaService.HEADER_WAREHOUSE_ID, c.saga().getWarehouseId()
                );
                c.saga().onStockReserved(warehouseId);
            }
        };

        var stockRejectedAction = new ContextAwareAction<SagaState, SagaEvent, SagaContext>() {
            @Override
            protected void run(SagaContext c, StateContext<SagaState, SagaEvent> ctx) {
                String reason = StateContexts.headerOrDefault(ctx, OrderSagaService.HEADER_REASON, "Stock insuficiente");
                c.saga().onStockRejected(reason);
            }
        };

        var confirmReservationAction = new ContextAwareAction<SagaState, SagaEvent, SagaContext>() {
            @Override
            protected void run(SagaContext c, StateContext<SagaState, SagaEvent> ctx) {
                String shipmentId = StateContexts.headerOrDefault(ctx, OrderSagaService.HEADER_SHIPMENT_ID, "");
                String trackingNumber = StateContexts.headerOrDefault(ctx, OrderSagaService.HEADER_TRACKING_NUMBER, "");
                String driverId = StateContexts.headerOrDefault(ctx, OrderSagaService.HEADER_DRIVER_ID, "");
                c.saga().onDeliveryScheduled(shipmentId, trackingNumber, driverId);
            }
        };

        var pickedUpAction = new ContextAwareAction<SagaState, SagaEvent, SagaContext>() {
            @Override
            protected void run(SagaContext c, StateContext<SagaState, SagaEvent> ctx) {
                String driverId = StateContexts.headerOrDefault(ctx, OrderSagaService.HEADER_DRIVER_ID, c.saga().getDriverId());
                c.saga().onShipmentPickedUp(driverId);
            }
        };

        var deliveredAction = new ContextAwareAction<SagaState, SagaEvent, SagaContext>() {
            @Override
            protected void run(SagaContext c, StateContext<SagaState, SagaEvent> ctx) {
                c.saga().onShipmentDelivered();
            }
        };

        var cancelShipmentAction = new ContextAwareAction<SagaState, SagaEvent, SagaContext>() {
            @Override
            protected void run(SagaContext c, StateContext<SagaState, SagaEvent> ctx) {
                String reason = StateContexts.headerOrDefault(ctx, OrderSagaService.HEADER_REASON, "Cancelación solicitada");
                c.saga().requestCancelShipment(reason);
            }
        };

        var releaseStockAction = new ContextAwareAction<SagaState, SagaEvent, SagaContext>() {
            @Override
            protected void run(SagaContext c, StateContext<SagaState, SagaEvent> ctx) {
                String reason = StateContexts.headerOrDefault(ctx, OrderSagaService.HEADER_REASON, c.saga().getFailureReason());
                c.saga().requestReleaseStock(reason);
            }
        };

        transitions
                // 1. Inicio de la saga -> solicita reserva de stock
                .withExternal()
                .source(SagaState.NEW).target(SagaState.RESERVING_STOCK)
                .event(SagaEvent.START)
                .action(reserveStockAction)

                // 2. Respuesta exitosa de Warehouse -> solicita despacho a Dispatch
                .and()
                .withExternal()
                .source(SagaState.RESERVING_STOCK).target(SagaState.SCHEDULING_DELIVERY)
                .event(SagaEvent.STOCK_RESERVED)
                .action(scheduleDeliveryAction)

                // 3. Rechazo por falta de stock -> fin REJECTED
                .and()
                .withExternal()
                .source(SagaState.RESERVING_STOCK).target(SagaState.REJECTED)
                .event(SagaEvent.STOCK_REJECTED)
                .action(stockRejectedAction)

                // 4. Cancelación o timeout mientras reserva stock -> libera stock (tombstone o reversión)
                .and()
                .withExternal()
                .source(SagaState.RESERVING_STOCK).target(SagaState.RELEASING_STOCK)
                .event(SagaEvent.CANCEL_REQUESTED)
                .action(releaseStockAction)
                .and()
                .withExternal()
                .source(SagaState.RESERVING_STOCK).target(SagaState.RELEASING_STOCK)
                .event(SagaEvent.TIMEOUT)
                .action(releaseStockAction)

                // 5. Despacho programado con éxito -> confirma reserva en Warehouse y espera retiro
                .and()
                .withExternal()
                .source(SagaState.SCHEDULING_DELIVERY).target(SagaState.AWAITING_PICKUP)
                .event(SagaEvent.DELIVERY_SCHEDULED)
                .action(confirmReservationAction)

                // 6. Fallo al asignar ruta/conductor -> compensación liberando stock en Warehouse
                .and()
                .withExternal()
                .source(SagaState.SCHEDULING_DELIVERY).target(SagaState.RELEASING_STOCK)
                .event(SagaEvent.DELIVERY_FAILED)
                .action(releaseStockAction)

                // 7. Cancelación o timeout mientras programa envío -> cancela envío en Dispatch
                .and()
                .withExternal()
                .source(SagaState.SCHEDULING_DELIVERY).target(SagaState.CANCELLING_SHIPMENT)
                .event(SagaEvent.CANCEL_REQUESTED)
                .action(cancelShipmentAction)
                .and()
                .withExternal()
                .source(SagaState.SCHEDULING_DELIVERY).target(SagaState.CANCELLING_SHIPMENT)
                .event(SagaEvent.TIMEOUT)
                .action(cancelShipmentAction)

                // 8. Retiro por conductor -> pasa a IN_TRANSIT
                .and()
                .withExternal()
                .source(SagaState.AWAITING_PICKUP).target(SagaState.IN_TRANSIT)
                .event(SagaEvent.SHIPMENT_PICKED_UP)
                .action(pickedUpAction)

                // 9. Cancelación manual antes del retiro -> cancela envío en Dispatch
                .and()
                .withExternal()
                .source(SagaState.AWAITING_PICKUP).target(SagaState.CANCELLING_SHIPMENT)
                .event(SagaEvent.CANCEL_REQUESTED)
                .action(cancelShipmentAction)

                // 10. Confirmación de envío cancelado -> libera stock en Warehouse
                .and()
                .withExternal()
                .source(SagaState.CANCELLING_SHIPMENT).target(SagaState.RELEASING_STOCK)
                .event(SagaEvent.SHIPMENT_CANCELLED)
                .action(releaseStockAction)
                .and()
                .withExternal()
                .source(SagaState.CANCELLING_SHIPMENT).target(SagaState.RELEASING_STOCK)
                .event(SagaEvent.TIMEOUT)
                .action(releaseStockAction)

                // 11. Entrega exitosa -> fin COMPLETED
                .and()
                .withExternal()
                .source(SagaState.IN_TRANSIT).target(SagaState.COMPLETED)
                .event(SagaEvent.SHIPMENT_DELIVERED)
                .action(deliveredAction)

                // 12. Fallo de entrega en calle (D10) -> devuelve el stock al Warehouse
                .and()
                .withExternal()
                .source(SagaState.IN_TRANSIT).target(SagaState.RELEASING_STOCK)
                .event(SagaEvent.DELIVERY_FAILED)
                .action(releaseStockAction)

                // 13. Stock liberado en Warehouse -> fin CANCELLED
                .and()
                .withExternal()
                .source(SagaState.RELEASING_STOCK).target(SagaState.CANCELLED)
                .event(SagaEvent.STOCK_RELEASED);
    }
}
