package com.fastdrop.dispatch.application.port.in.command;

public record ReportDeliveryFailureCommand(
        String orderId,
        String reason
) {
}
