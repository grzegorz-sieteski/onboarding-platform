package com.gs5.restriction.infrastructure.eventstore;

import java.time.Instant;

public record NewEvent(String type, String payload, Instant occurredAt) {
}
