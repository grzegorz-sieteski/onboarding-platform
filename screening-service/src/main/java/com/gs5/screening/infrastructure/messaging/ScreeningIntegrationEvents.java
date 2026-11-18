package com.gs5.screening.infrastructure.messaging;

import com.gs5.screening.domain.ScreeningCase;
import com.gs5.screening.domain.ScreeningEvent;
import com.gs5.screening.domain.ScreeningEvent.BlacklistMatchFound;
import com.gs5.screening.domain.ScreeningEvent.ScreeningPassed;
import com.gs5.screening.domain.ScreeningEvent.ScreeningRequested;
import com.gs5.screening.infrastructure.messaging.PublishedEvents.ClientClearedV1;
import com.gs5.screening.infrastructure.messaging.PublishedEvents.ClientRestrictedV1;
import com.gs5.screening.infrastructure.outbox.OutboxMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ScreeningIntegrationEvents {

    private final IntegrationEventCodec codec;
    private final String topic;

    ScreeningIntegrationEvents(IntegrationEventCodec codec, MessagingProperties properties) {
        this.codec = codec;
        this.topic = properties.topics().screeningEvents();
    }

    /** Must be called before the events are marked as committed - versions are derived from the aggregate. */
    public List<OutboxMessage> toOutboxMessages(ScreeningCase screening, List<ScreeningEvent> newEvents) {
        var messages = new ArrayList<OutboxMessage>();
        long version = screening.version();
        for (ScreeningEvent event : newEvents) {
            long eventVersion = ++version;
            toContract(event)
                    .map(contract -> codec.encode(topic, contract.type(), screening.customerId().toString(),
                            eventVersion, event.occurredAt(), contract.payload()))
                    .ifPresent(messages::add);
        }
        return messages;
    }

    private static Optional<Contract> toContract(ScreeningEvent event) {
        return switch (event) {
            case BlacklistMatchFound match -> Optional.of(new Contract(PublishedEvents.CLIENT_RESTRICTED,
                    new ClientRestrictedV1(match.customerId().toString(), match.matchedSurname(), match.reason())));
            case ScreeningPassed passed -> Optional.of(new Contract(PublishedEvents.CLIENT_CLEARED,
                    new ClientClearedV1(passed.customerId().toString())));
            case ScreeningRequested internalOnly -> Optional.empty();
        };
    }

    private record Contract(String type, Object payload) {
    }
}
