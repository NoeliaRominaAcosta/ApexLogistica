package com.fastdrop.dispatch.application.port.out;

public interface RoutingPort {
    RouteResult calculateRouteAndAssignDriver(String orderId, String deliveryAddress);
    void setForceFailure(boolean fail);
    boolean isForceFailure();
}
