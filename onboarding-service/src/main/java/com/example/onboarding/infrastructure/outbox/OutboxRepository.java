package com.example.onboarding.infrastructure.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Transactional outbox. Messages are written in the same local transaction as the events in the event store,
 * so "state changed" and "message will be published" are atomic - no dual-write problem.
 */
@Component
public class OutboxRepository {

    private final JdbcClient jdbc;

    public OutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(OutboxMessage message, Instant createdAt) {
        jdbc.sql("""
                        INSERT INTO outbox (message_id, topic, message_key, event_type, payload, created_at)
                        VALUES (:messageId, :topic, :key, :eventType, :payload, :createdAt)
                        """)
                .param("messageId", message.messageId())
                .param("topic", message.topic())
                .param("key", message.key())
                .param("eventType", message.eventType())
                .param("payload", message.payload())
                .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .update();
    }

    List<OutboxEntry> findUnpublished(int limit) {
        return jdbc.sql("""
                        SELECT id, message_id, topic, message_key, event_type, payload
                          FROM outbox
                         WHERE published_at IS NULL
                         ORDER BY id
                         LIMIT :limit
                        """)
                .param("limit", limit)
                .query((rs, rowNum) -> new OutboxEntry(
                        rs.getLong("id"),
                        new OutboxMessage(
                                rs.getObject("message_id", UUID.class),
                                rs.getString("topic"),
                                rs.getString("message_key"),
                                rs.getString("event_type"),
                                rs.getString("payload"))))
                .list();
    }

    void markPublished(long position, Instant publishedAt) {
        jdbc.sql("UPDATE outbox SET published_at = :publishedAt WHERE id = :position")
                .param("publishedAt", OffsetDateTime.ofInstant(publishedAt, ZoneOffset.UTC))
                .param("position", position)
                .update();
    }
}
