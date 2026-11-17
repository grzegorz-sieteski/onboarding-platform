package com.gs5.screening.infrastructure.eventstore;

import java.time.Instant;

public record NewEvent(String type, String payload, Instant occurredAt) {
}
