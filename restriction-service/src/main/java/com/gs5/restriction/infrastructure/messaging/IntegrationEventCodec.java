package com.gs5.restriction.infrastructure.messaging;

import com.gs5.restriction.domain.shared.Result;
import com.gs5.restriction.infrastructure.outbox.OutboxMessage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

public class IntegrationEventCodec {

    private final ObjectMapper objectMapper;

    public IntegrationEventCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Our own event is always serializable; a failure here is a bug and is allowed to surface as one. */
    public OutboxMessage encode(String topic, String eventType, String aggregateId, long aggregateVersion,
                                Instant occurredAt, Object payload) {
        var event = new IntegrationEvent(UUID.randomUUID(), eventType, aggregateId, aggregateVersion, occurredAt,
                objectMapper.valueToTree(payload));
        return new OutboxMessage(event.eventId(), topic, aggregateId, eventType,
                objectMapper.writeValueAsString(event));
    }

    /** Input from another service is untrusted: a message that cannot be understood is a value, not an exception. */
    public Result<IntegrationEvent, String> decode(String json) {
        if (json == null) {
            return Result.failure("Empty message");
        }
        IntegrationEvent event;
        try {
            event = objectMapper.readValue(json, IntegrationEvent.class);
        } catch (JacksonException _) {
            return Result.failure("Not an integration event: " + json);
        }
        if (event.eventId() == null || event.eventType() == null || event.payload() == null) {
            return Result.failure("Incomplete integration event envelope: " + json);
        }
        return Result.success(event);
    }

    public <T> Result<T, String> payloadOf(IntegrationEvent event, Class<T> contract) {
        try {
            return Result.success(objectMapper.treeToValue(event.payload(), contract));
        } catch (JacksonException | IllegalArgumentException _) {
            return Result.failure("Payload of %s does not match %s"
                    .formatted(event.eventId(), contract.getSimpleName()));
        }
    }
}
