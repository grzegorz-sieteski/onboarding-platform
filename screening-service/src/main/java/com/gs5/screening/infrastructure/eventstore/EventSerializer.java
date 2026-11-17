package com.gs5.screening.infrastructure.eventstore;

import com.gs5.screening.domain.shared.DomainEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JSON (de)serialization of a <b>sealed</b> family of domain events. The type registry is derived from the permitted
 * subclasses, so adding a new event record to the sealed interface is all it takes to make it storable.
 */
public final class EventSerializer<E extends DomainEvent> {

    private final ObjectMapper objectMapper;
    private final Map<String, Class<? extends E>> eventTypesByName;

    public EventSerializer(ObjectMapper objectMapper, Class<E> sealedEventType) {
        if (!sealedEventType.isSealed()) {
            throw new IllegalArgumentException(sealedEventType + " must be a sealed interface");
        }
        this.objectMapper = objectMapper;
        this.eventTypesByName = Arrays.stream(sealedEventType.getPermittedSubclasses())
                .map(type -> type.asSubclass(sealedEventType))
                .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, Function.identity()));
    }

    public NewEvent toNewEvent(E event) {
        try {
            return new NewEvent(event.getClass().getSimpleName(), objectMapper.writeValueAsString(event),
                    event.occurredAt());
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize event " + event, e);
        }
    }

    public E deserialize(StoredEvent storedEvent) {
        Class<? extends E> type = eventTypesByName.get(storedEvent.type());
        if (type == null) {
            throw new IllegalStateException("Unknown event type in stream %s: %s"
                    .formatted(storedEvent.streamId(), storedEvent.type()));
        }
        try {
            return objectMapper.readValue(storedEvent.payload(), type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Corrupted event %s v%d".formatted(storedEvent.streamId(),
                    storedEvent.version()), e);
        }
    }
}
