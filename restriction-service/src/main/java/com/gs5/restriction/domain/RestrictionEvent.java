package com.gs5.restriction.domain;

import com.gs5.restriction.domain.shared.DomainEvent;

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
