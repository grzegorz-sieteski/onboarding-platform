package com.gs5.screening.infrastructure.messaging;

import com.gs5.screening.application.ScreenCustomerCommand;
import com.gs5.screening.application.ScreeningCommandHandler;
import com.gs5.screening.domain.CustomerId;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Inbound adapter for the Onboarding context: every verified applicant gets screened before activation. */
@Component
class OnboardingEventsListener {

    static final String APPLICANT_VERIFIED = "ApplicantVerified";

    private static final Logger log = LoggerFactory.getLogger(OnboardingEventsListener.class);

    private final IntegrationEventCodec codec;
    private final InboxProcessor inbox;
    private final ScreeningCommandHandler commands;

    OnboardingEventsListener(IntegrationEventCodec codec, InboxProcessor inbox, ScreeningCommandHandler commands) {
        this.codec = codec;
        this.inbox = inbox;
        this.commands = commands;
    }

    @KafkaListener(topics = "${app.kafka.topics.onboarding-events}")
    void on(ConsumerRecord<String, String> record) {
        IntegrationEvent event = codec.decode(record.value()).orElseThrow(InboundFailures::unusable);
        if (!APPLICANT_VERIFIED.equals(event.eventType())) {
            log.debug("Ignoring {} - not relevant for screening", event.eventType());
            return;
        }
        ApplicantVerifiedV1 verified = codec.payloadOf(event, ApplicantVerifiedV1.class)
                .orElseThrow(InboundFailures::unusable);
        inbox.processOnce(event.eventId(), () -> CustomerId.of(verified.customerId())
                .flatMap(id -> commands.handle(new ScreenCustomerCommand(id, verified.firstName(), verified.lastName())))
                .orElseThrow(InboundFailures::refused));
    }

    /** This context's own view of the upstream contract - only the fields it needs (tolerant reader). */
    record ApplicantVerifiedV1(String customerId, String firstName, String lastName) {
    }
}
