package com.example.screening.infrastructure.messaging;

import com.example.screening.application.ScreenCustomerCommand;
import com.example.screening.application.ScreeningCommandHandler;
import com.example.screening.domain.CustomerId;
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
        IntegrationEvent event = codec.decode(record.value());
        if (!APPLICANT_VERIFIED.equals(event.eventType())) {
            log.debug("Ignoring {} - not relevant for screening", event.eventType());
            return;
        }
        ApplicantVerifiedV1 verified = codec.payloadOf(event, ApplicantVerifiedV1.class);
        inbox.processOnce(event.eventId(), () -> commands.handle(new ScreenCustomerCommand(
                CustomerId.of(verified.customerId()), verified.firstName(), verified.lastName())));
    }

    /** This context's own view of the upstream contract - only the fields it needs (tolerant reader). */
    record ApplicantVerifiedV1(String customerId, String firstName, String lastName) {
    }
}
