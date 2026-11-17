package com.gs5.onboarding.infrastructure.eventstore;

import com.gs5.onboarding.domain.shared.Result;
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
 * that loaded the same version cannot both append - the second one gets a {@link StreamConflict}.
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

    /**
     * Appends the events one after another and stops at the first version that is already taken.
     * <p>
     * A lost race is an expected outcome, so it is a value. Nothing is rolled back here: after a failed statement
     * the database (PostgreSQL does) may already have aborted the transaction, and the events written before the
     * conflict are still in it. The caller owns the transaction and has to roll it back.
     */
    public Result<Void, StreamConflict> append(String streamId, long expectedVersion, List<NewEvent> events) {
        long version = expectedVersion;
        for (NewEvent event : events) {
            version++;
            if (!insert(streamId, version, event)) {
                return Result.failure(new StreamConflict(streamId, expectedVersion));
            }
        }
        return Result.success(null);
    }

    private boolean insert(String streamId, long version, NewEvent event) {
        try {
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
            return true;
        } catch (DuplicateKeyException _) {
            return false;
        }
    }
}
