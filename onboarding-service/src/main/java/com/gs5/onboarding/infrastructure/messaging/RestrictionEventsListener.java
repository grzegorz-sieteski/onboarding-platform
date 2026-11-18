package com.gs5.onboarding.infrastructure.messaging;

import com.gs5.onboarding.application.BanOnboardingCommand;
import com.gs5.onboarding.application.OnboardingCommandHandler;
import com.gs5.onboarding.domain.CustomerId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inbound adapter for the Restriction context. Messages of one customer share a partition and a partition is
 * consumed by exactly one thread, so they are handled strictly in the order they were published.
 */
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

    /** Throws only to tell the listener container what to do with the message: dead-letter it or retry it. */
    void on(String message) {
        IntegrationEvent event = codec.decode(message).orElseThrow(InboundFailures::unusable);
        if (!CLIENT_BANNED.equals(event.eventType())) {
            log.debug("Ignoring {} - not relevant for onboarding", event.eventType());
            return;
        }
        ClientBannedV1 clientBanned = codec.payloadOf(event, ClientBannedV1.class)
                .orElseThrow(InboundFailures::unusable);
        inbox.processOnce(event.eventId(), () -> CustomerId.of(clientBanned.customerId())
                        .flatMap(id -> commands.handle(new BanOnboardingCommand(id, clientBanned.reason()))))
                .orElseThrow(InboundFailures::refused);
    }

    /** This context's own view of the upstream contract - only the fields it needs (tolerant reader). */
    record ClientBannedV1(String customerId, String reason) {
    }
}
