package com.gs5.screening.infrastructure.eventstore;

import com.gs5.screening.domain.shared.DomainEvent;
import com.gs5.screening.domain.shared.Result;
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
            // wiring mistake, caught when the application starts
            throw new IllegalArgumentException(sealedEventType + " must be a sealed interface");
        }
        this.objectMapper = objectMapper;
        this.eventTypesByName = Arrays.stream(sealedEventType.getPermittedSubclasses())
                .map(type -> type.asSubclass(sealedEventType))
                .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, Function.identity()));
    }

    /** Our own event is always serializable; a failure here is a bug and is allowed to surface as one. */
    public NewEvent toNewEvent(E event) {
        return new NewEvent(event.getClass().getSimpleName(), objectMapper.writeValueAsString(event),
                event.occurredAt());
    }

    /** The failure reason is meant for an operator: the stored event cannot be turned back into a domain event. */
    public Result<E, String> deserialize(StoredEvent storedEvent) {
        Class<? extends E> type = eventTypesByName.get(storedEvent.type());
        if (type == null) {
            return Result.failure("unknown event type %s at version %d"
                    .formatted(storedEvent.type(), storedEvent.version()));
        }
        try {
            return Result.success(objectMapper.readValue(storedEvent.payload(), type));
        } catch (JacksonException e) {
            return Result.failure("event %s at version %d cannot be read: %s"
                    .formatted(storedEvent.type(), storedEvent.version(), e.getMessage()));
        }
    }
}
