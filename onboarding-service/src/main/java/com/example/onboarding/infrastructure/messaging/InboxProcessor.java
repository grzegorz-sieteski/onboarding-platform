package com.example.onboarding.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Idempotent consumer (inbox pattern). The handler and the "processed" marker are committed in one transaction,
 * so a redelivered message (producer retry, consumer rebalance, outbox re-send) is applied exactly once.
 */
@Component
public class InboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(InboxProcessor.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public InboxProcessor(JdbcClient jdbc, TransactionTemplate transaction, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.clock = clock;
    }

    public void processOnce(UUID messageId, Runnable handler) {
        transaction.executeWithoutResult(status -> {
            if (alreadyProcessed(messageId)) {
                log.info("Skipping duplicate message {}", messageId);
                return;
            }
            handler.run();
            markProcessed(messageId);
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
