package com.gs5.screening.infrastructure.messaging;

import com.gs5.screening.application.ScreenCustomerCommand;
import com.gs5.screening.application.ScreeningCommandHandler;
import com.gs5.screening.domain.CustomerId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Inbound adapter for the Onboarding context: every verified applicant gets screened before activation. */
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

    /** Throws only to tell the listener container what to do with the message: dead-letter it or retry it. */
    void on(String message) {
        IntegrationEvent event = codec.decode(message).orElseThrow(InboundFailures::unusable);
        if (!APPLICANT_VERIFIED.equals(event.eventType())) {
            log.debug("Ignoring {} - not relevant for screening", event.eventType());
            return;
        }
        ApplicantVerifiedV1 verified = codec.payloadOf(event, ApplicantVerifiedV1.class)
                .orElseThrow(InboundFailures::unusable);
        inbox.processOnce(event.eventId(), () -> CustomerId.of(verified.customerId())
                        .flatMap(id -> commands.handle(
                                new ScreenCustomerCommand(id, verified.firstName(), verified.lastName()))))
                .orElseThrow(InboundFailures::refused);
    }

    /** This context's own view of the upstream contract - only the fields it needs (tolerant reader). */
    record ApplicantVerifiedV1(String customerId, String firstName, String lastName) {
    }
}
