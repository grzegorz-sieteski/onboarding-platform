package com.example.screening.infrastructure.eventstore;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Append-only event store on a relational database.
 * <p>
 * Optimistic concurrency: the unique constraint on {@code (stream_id, stream_version)} guarantees that two writers
 * that loaded the same version cannot both append - the second one gets {@link ConcurrencyConflictException}.
 * This is also what keeps events of a single aggregate totally ordered.
 */
@Component
public class JdbcEventStore {

    private final JdbcClient jdbc;

    public JdbcEventStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<StoredEvent> readStream(String streamId) {
        return jdbc.sql("""
                        SELECT stream_id, stream_version, event_type, payload, occurred_at
                          FROM event_store
                         WHERE stream_id = :streamId
                         ORDER BY stream_version
                        """)
                .param("streamId", streamId)
                .query((rs, rowNum) -> new StoredEvent(
                        rs.getString("stream_id"),
                        rs.getLong("stream_version"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    public boolean streamExists(String streamId) {
        return jdbc.sql("SELECT COUNT(*) FROM event_store WHERE stream_id = :streamId")
                .param("streamId", streamId)
                .query(Long.class)
                .single() > 0;
    }

    public void append(String streamId, long expectedVersion, List<NewEvent> events) {
        long version = expectedVersion;
        for (NewEvent event : events) {
            version++;
            try {
                insert(streamId, version, event);
            } catch (DuplicateKeyException e) {
                throw new ConcurrencyConflictException(streamId, expectedVersion, e);
            }
        }
    }

    private void insert(String streamId, long version, NewEvent event) {
        jdbc.sql("""
                        INSERT INTO event_store (stream_id, stream_version, event_type, payload, occurred_at)
                        VALUES (:streamId, :version, :type, :payload, :occurredAt)
                        """)
                .param("streamId", streamId)
                .param("version", version)
                .param("type", event.type())
                .param("payload", event.payload())
                .param("occurredAt", OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                .update();
    }
}
