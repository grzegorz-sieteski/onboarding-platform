package com.gs5.restriction.domain.shared;

import java.time.Instant;

/** A fact that happened in the domain. Immutable, named in past tense. */
public interface DomainEvent {

    Instant occurredAt();
}
