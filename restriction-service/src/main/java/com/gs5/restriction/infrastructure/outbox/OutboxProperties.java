package com.gs5.restriction.infrastructure.outbox;

import java.time.Duration;

/**
 * Configuration of the outbox relay, bound from {@code app.outbox}.
 *
 * @param batchSize      max messages relayed per run
 * @param sendTimeout    how long one send may take before the batch stops and is retried
 * @param pollIntervalMs pause between two runs of the relay
 */
public record OutboxProperties(int batchSize, Duration sendTimeout, long pollIntervalMs) {
}
