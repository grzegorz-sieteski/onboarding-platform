package com.gs5.screening.infrastructure.eventstore;

import java.time.Instant;

public record StoredEvent(String streamId, long version, String type, String payload, Instant occurredAt) {
}
