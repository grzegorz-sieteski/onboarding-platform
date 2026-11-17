package com.example.restriction.infrastructure.outbox;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Relays outbox messages to Kafka, preserving their order.
 * <ul>
 *     <li>Only one instance of the service relays at a time (ShedLock) - running replicas cannot reorder messages.</li>
 *     <li>Messages are sent one by one, synchronously, in outbox order. On the first failure the batch stops, so a
 *     later message can never overtake an earlier one; the next run resumes from the failed message.</li>
 *     <li>Key = aggregate id, so all events of one customer land in one partition, in order.</li>
 *     <li>Delivery is at-least-once (crash between send and mark) - consumers deduplicate by message id.</li>
 * </ul>
 */
@Component
class OutboxRelay {

    static final String EVENT_TYPE_HEADER = "event-type";
    static final String MESSAGE_ID_HEADER = "message-id";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;
    private final Clock clock;

    OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, OutboxProperties properties,
                Clock clock) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:200}")
    @SchedulerLock(name = "outbox-relay", lockAtMostFor = "PT1M")
    void relayPendingMessages() {
        for (OutboxEntry entry : outbox.findUnpublished(properties.batchSize())) {
            if (!publish(entry.message())) {
                return;
            }
            outbox.markPublished(entry.position(), clock.instant());
        }
    }

    private boolean publish(OutboxMessage message) {
        try {
            kafka.send(toRecord(message)).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Publishing {} to {} failed, will retry", message.messageId(), message.topic(), e);
            return false;
        }
    }

    private static ProducerRecord<String, String> toRecord(OutboxMessage message) {
        var record = new ProducerRecord<>(message.topic(), message.key(), message.payload());
        record.headers()
                .add(EVENT_TYPE_HEADER, message.eventType().getBytes(StandardCharsets.UTF_8))
                .add(MESSAGE_ID_HEADER, message.messageId().toString().getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
