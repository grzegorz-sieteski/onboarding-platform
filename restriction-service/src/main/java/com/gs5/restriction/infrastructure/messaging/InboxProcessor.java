package com.gs5.restriction.infrastructure.messaging;

import com.gs5.restriction.application.UnitOfWork;
import com.gs5.restriction.domain.shared.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Idempotent consumer (inbox pattern). The handler and the "processed" marker are committed in one unit of work,
 * so a redelivered message (producer retry, consumer rebalance, outbox re-send) is applied exactly once. A handler
 * that fails rolls back with it and the message is not marked, so it can be retried or dead-lettered.
 */
public class InboxProcessor {

    /** What happened to a delivered message. */
    public enum Delivery {
        PROCESSED,
        DUPLICATE_SKIPPED
    }

    private static final Logger log = LoggerFactory.getLogger(InboxProcessor.class);

    private final JdbcClient jdbc;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public InboxProcessor(JdbcClient jdbc, UnitOfWork unitOfWork, Clock clock) {
        this.jdbc = jdbc;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public <T, E> Result<Delivery, E> processOnce(UUID messageId, Supplier<Result<T, E>> handler) {
        return unitOfWork.inTransaction(() -> {
            if (alreadyProcessed(messageId)) {
                log.info("Skipping duplicate message {}", messageId);
                return Result.success(Delivery.DUPLICATE_SKIPPED);
            }
            return handler.get().map(_ -> {
                markProcessed(messageId);
                return Delivery.PROCESSED;
            });
        });
    }

    private boolean alreadyProcessed(UUID messageId) {
        return jdbc.sql("SELECT COUNT(*) FROM processed_message WHERE message_id = :messageId")
                .param("messageId", messageId)
                .query(Long.class)
                .single() > 0;
    }

    private void markProcessed(UUID messageId) {
        jdbc.sql("INSERT INTO processed_message (message_id, processed_at) VALUES (:messageId, :processedAt)")
                .param("messageId", messageId)
                .param("processedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
    }
}
