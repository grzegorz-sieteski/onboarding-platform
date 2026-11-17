package com.example.restriction.domain;

import com.example.restriction.domain.shared.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public sealed interface RestrictionEvent extends DomainEvent {

    UUID customerId();

    record ClientBanned(UUID customerId, String reason, RestrictionSource source, Instant occurredAt)
            implements RestrictionEvent {
    }

    record BanRequestIgnored(UUID customerId, String reason, RestrictionSource source, Instant occurredAt)
            implements RestrictionEvent {
    }
}
