package com.gs5.restriction.infrastructure.messaging;

import com.gs5.restriction.domain.ClientRestriction;
import com.gs5.restriction.domain.RestrictionEvent;
import com.gs5.restriction.domain.RestrictionEvent.BanRequestIgnored;
import com.gs5.restriction.domain.RestrictionEvent.ClientBanned;
import com.gs5.restriction.infrastructure.messaging.PublishedEvents.ClientBannedV1;
import com.gs5.restriction.infrastructure.outbox.OutboxMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class RestrictionIntegrationEvents {

    private final IntegrationEventCodec codec;
    private final String topic;

    RestrictionIntegrationEvents(IntegrationEventCodec codec, MessagingProperties properties) {
        this.codec = codec;
        this.topic = properties.topics().restrictionEvents();
    }

    /** Must be called before the events are marked as committed - versions are derived from the aggregate. */
    public List<OutboxMessage> toOutboxMessages(ClientRestriction restriction, List<RestrictionEvent> newEvents) {
        var messages = new ArrayList<OutboxMessage>();
        long version = restriction.version();
        for (RestrictionEvent event : newEvents) {
            long eventVersion = ++version;
            toContract(event)
                    .map(contract -> codec.encode(topic, contract.type(), restriction.customerId().toString(),
                            eventVersion, event.occurredAt(), contract.payload()))
                    .ifPresent(messages::add);
        }
        return messages;
    }

    private static Optional<Contract> toContract(RestrictionEvent event) {
        return switch (event) {
            case ClientBanned banned -> Optional.of(new Contract(PublishedEvents.CLIENT_BANNED,
                    new ClientBannedV1(banned.customerId().toString(), banned.reason(), banned.source().name())));
            case BanRequestIgnored internalOnly -> Optional.empty();
        };
    }

    private record Contract(String type, Object payload) {
    }
}
