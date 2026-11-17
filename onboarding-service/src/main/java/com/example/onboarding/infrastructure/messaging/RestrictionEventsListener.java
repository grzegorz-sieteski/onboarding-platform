package com.example.onboarding.infrastructure.messaging;

import com.example.onboarding.application.BanOnboardingCommand;
import com.example.onboarding.application.OnboardingCommandHandler;
import com.example.onboarding.domain.CustomerId;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter for the Restriction context. Messages of one customer share a partition and a partition is
 * consumed by exactly one thread, so they are handled strictly in the order they were published.
 */
@Component
class RestrictionEventsListener {

    static final String CLIENT_BANNED = "ClientBanned";

    private static final Logger log = LoggerFactory.getLogger(RestrictionEventsListener.class);

    private final IntegrationEventCodec codec;
    private final InboxProcessor inbox;
    private final OnboardingCommandHandler commands;

    RestrictionEventsListener(IntegrationEventCodec codec, InboxProcessor inbox, OnboardingCommandHandler commands) {
        this.codec = codec;
        this.inbox = inbox;
        this.commands = commands;
    }

    @KafkaListener(topics = "${app.kafka.topics.restriction-events}")
    void on(ConsumerRecord<String, String> record) {
        IntegrationEvent event = codec.decode(record.value());
        if (!CLIENT_BANNED.equals(event.eventType())) {
            log.debug("Ignoring {} - not relevant for onboarding", event.eventType());
            return;
        }
        ClientBannedV1 clientBanned = codec.payloadOf(event, ClientBannedV1.class);
        inbox.processOnce(event.eventId(), () -> commands.handle(
                new BanOnboardingCommand(CustomerId.of(clientBanned.customerId()), clientBanned.reason())));
    }

    /** This context's own view of the upstream contract - only the fields it needs (tolerant reader). */
    record ClientBannedV1(String customerId, String reason) {
    }
}
