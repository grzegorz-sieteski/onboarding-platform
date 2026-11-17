package com.gs5.restriction.infrastructure.messaging;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope of every message exchanged between contexts (the published language).
 *
 * @param eventId          unique id - consumers use it for deduplication (at-least-once delivery)
 * @param eventType        name of the payload contract, e.g. {@code CustomerOnboarded}
 * @param aggregateId      id of the aggregate that emitted the event - also the Kafka message key
 * @param aggregateVersion position of the event in its aggregate stream - lets consumers detect gaps/reordering
 * @param payload          contract-specific body; consumers read only the fields they need (tolerant reader)
 */
public record IntegrationEvent(UUID eventId, String eventType, String aggregateId, long aggregateVersion,
                               Instant occurredAt, JsonNode payload) {
}
