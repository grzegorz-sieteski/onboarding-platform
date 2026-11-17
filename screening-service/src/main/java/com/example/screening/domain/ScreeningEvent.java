package com.example.screening.domain;

import com.example.screening.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public sealed interface ScreeningEvent extends DomainEvent {

    UUID customerId();

    record ScreeningRequested(UUID customerId, String firstName, String lastName, Instant occurredAt)
            implements ScreeningEvent {
    }

    record ScreeningPassed(UUID customerId, Instant occurredAt) implements ScreeningEvent {
    }

    record BlacklistMatchFound(UUID customerId, String matchedSurname, String reason, Instant occurredAt)
            implements ScreeningEvent {
    }
}
