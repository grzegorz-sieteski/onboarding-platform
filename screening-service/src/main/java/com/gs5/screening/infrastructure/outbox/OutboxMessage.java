package com.gs5.screening.infrastructure.outbox;

import java.util.UUID;

/** A message waiting to be relayed to Kafka. {@code key} decides the partition, hence the ordering scope. */
public record OutboxMessage(UUID messageId, String topic, String key, String eventType, String payload) {
}
