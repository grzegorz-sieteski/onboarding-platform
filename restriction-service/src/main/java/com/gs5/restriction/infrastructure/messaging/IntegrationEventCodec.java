package com.gs5.restriction.infrastructure.messaging;

import com.gs5.restriction.infrastructure.outbox.OutboxMessage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class IntegrationEventCodec {

    private final ObjectMapper objectMapper;

    public IntegrationEventCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxMessage encode(String topic, String eventType, String aggregateId, long aggregateVersion,
                                Instant occurredAt, Object payload) {
        var event = new IntegrationEvent(UUID.randomUUID(), eventType, aggregateId, aggregateVersion, occurredAt,
                objectMapper.valueToTree(payload));
        try {
            return new OutboxMessage(event.eventId(), topic, aggregateId, eventType,
                    objectMapper.writeValueAsString(event));
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize integration event " + eventType, e);
        }
    }

    public IntegrationEvent decode(String json) {
        try {
            IntegrationEvent event = objectMapper.readValue(json, IntegrationEvent.class);
            if (event.eventId() == null || event.eventType() == null || event.payload() == null) {
                throw new MalformedMessageException("Incomplete integration event envelope: " + json, null);
            }
            return event;
        } catch (JacksonException e) {
            throw new MalformedMessageException("Not an integration event: " + json, e);
        }
    }

    public <T> T payloadOf(IntegrationEvent event, Class<T> contract) {
        try {
            return objectMapper.treeToValue(event.payload(), contract);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new MalformedMessageException("Payload of %s does not match %s"
                    .formatted(event.eventId(), contract.getSimpleName()), e);
        }
    }
}
