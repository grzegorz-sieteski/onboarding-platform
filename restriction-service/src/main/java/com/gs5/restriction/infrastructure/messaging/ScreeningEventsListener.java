package com.gs5.restriction.infrastructure.messaging;

import com.gs5.restriction.application.BanClientCommand;
import com.gs5.restriction.application.RestrictionCommandHandler;
import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Inbound adapter for the Screening context: a restricted client gets banned. */
class ScreeningEventsListener {

    static final String CLIENT_RESTRICTED = "ClientRestricted";

    private static final Logger log = LoggerFactory.getLogger(ScreeningEventsListener.class);

    private final IntegrationEventCodec codec;
    private final InboxProcessor inbox;
    private final RestrictionCommandHandler commands;

    ScreeningEventsListener(IntegrationEventCodec codec, InboxProcessor inbox, RestrictionCommandHandler commands) {
        this.codec = codec;
        this.inbox = inbox;
        this.commands = commands;
    }

    /** Throws only to tell the listener container what to do with the message: dead-letter it or retry it. */
    void on(String message) {
        IntegrationEvent event = codec.decode(message).orElseThrow(InboundFailures::unusable);
        if (!CLIENT_RESTRICTED.equals(event.eventType())) {
            log.debug("Ignoring {} - not relevant for restrictions", event.eventType());
            return;
        }
        ClientRestrictedV1 restricted = codec.payloadOf(event, ClientRestrictedV1.class)
                .orElseThrow(InboundFailures::unusable);
        inbox.processOnce(event.eventId(), () -> CustomerId.of(restricted.customerId())
                        .flatMap(id -> commands.handle(
                                new BanClientCommand(id, restricted.reason(), RestrictionSource.SANCTIONS_SCREENING))))
                .orElseThrow(InboundFailures::refused);
    }

    /** This context's own view of the upstream contract - only the fields it needs (tolerant reader). */
    record ClientRestrictedV1(String customerId, String reason) {
    }
}
