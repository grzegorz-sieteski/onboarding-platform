package com.gs5.onboarding.infrastructure.messaging;

import com.gs5.onboarding.application.ConfirmScreeningClearedCommand;
import com.gs5.onboarding.application.OnboardingCommandHandler;
import com.gs5.onboarding.domain.CustomerId;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter for the Screening context: a cleared customer gets activated.
 * A negative verdict is deliberately NOT handled here - banning is the Restriction context's decision.
 */
@Component
class ScreeningEventsListener {

    static final String CLIENT_CLEARED = "ClientCleared";

    private static final Logger log = LoggerFactory.getLogger(ScreeningEventsListener.class);

    private final IntegrationEventCodec codec;
    private final InboxProcessor inbox;
    private final OnboardingCommandHandler commands;

    ScreeningEventsListener(IntegrationEventCodec codec, InboxProcessor inbox, OnboardingCommandHandler commands) {
        this.codec = codec;
        this.inbox = inbox;
        this.commands = commands;
    }

    @KafkaListener(topics = "${app.kafka.topics.screening-events}")
    void on(ConsumerRecord<String, String> record) {
        IntegrationEvent event = codec.decode(record.value());
        if (!CLIENT_CLEARED.equals(event.eventType())) {
            log.debug("Ignoring {} - not relevant for onboarding", event.eventType());
            return;
        }
        ClientClearedV1 cleared = codec.payloadOf(event, ClientClearedV1.class);
        inbox.processOnce(event.eventId(), () -> commands.handle(
                new ConfirmScreeningClearedCommand(CustomerId.of(cleared.customerId()))));
    }

    /** This context's own view of the upstream contract - only the fields it needs (tolerant reader). */
    record ClientClearedV1(String customerId) {
    }
}
