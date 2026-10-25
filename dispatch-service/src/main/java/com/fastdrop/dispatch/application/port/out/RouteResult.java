package com.fastdrop.dispatch.application.port.out;

import com.fastdrop.dispatch.domain.model.RouteAssignment;

public record RouteResult(
        boolean successful,
        RouteAssignment assignment,
        String failureReason,
        boolean fallbackTriggered
) {
}
