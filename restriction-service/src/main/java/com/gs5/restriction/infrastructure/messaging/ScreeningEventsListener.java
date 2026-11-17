package com.gs5.restriction.infrastructure.messaging;

import com.gs5.restriction.application.BanClientCommand;
import com.gs5.restriction.application.RestrictionCommandHandler;
import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionSource;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Inbound adapter for the Screening context: a restricted client gets banned. */
@Component
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

    @KafkaListener(topics = "${app.kafka.topics.screening-events}")
    void on(ConsumerRecord<String, String> record) {
        IntegrationEvent event = codec.decode(record.value());
        if (!CLIENT_RESTRICTED.equals(event.eventType())) {
            log.debug("Ignoring {} - not relevant for restrictions", event.eventType());
            return;
        }
        ClientRestrictedV1 restricted = codec.payloadOf(event, ClientRestrictedV1.class);
        inbox.processOnce(event.eventId(), () -> commands.handle(new BanClientCommand(
                CustomerId.of(restricted.customerId()), restricted.reason(), RestrictionSource.SANCTIONS_SCREENING)));
    }

    /** This context's own view of the upstream contract - only the fields it needs (tolerant reader). */
    record ClientRestrictedV1(String customerId, String reason) {
    }
}
