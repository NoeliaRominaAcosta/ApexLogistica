package com.fastdrop.orchestrator.application.port.out;

import com.fastdrop.orchestrator.domain.command.SagaOutboundCommand;

import java.util.List;

public interface SagaCommandPublisherPort {
    void publish(List<SagaOutboundCommand> commands);
}
