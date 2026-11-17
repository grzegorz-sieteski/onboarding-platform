package com.gs5.restriction.infrastructure.persistence;

import com.gs5.restriction.domain.ClientRestriction;
import com.gs5.restriction.domain.ClientRestrictionRepository;
import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionEvent;
import com.gs5.restriction.infrastructure.eventstore.EventSerializer;
import com.gs5.restriction.infrastructure.eventstore.JdbcEventStore;
import com.gs5.restriction.infrastructure.messaging.RestrictionIntegrationEvents;
import com.gs5.restriction.infrastructure.outbox.OutboxRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Repository
class EventSourcedClientRestrictionRepository implements ClientRestrictionRepository {

    private static final String STREAM_PREFIX = "client-restriction-";

    private final JdbcEventStore eventStore;
    private final EventSerializer<RestrictionEvent> serializer;
    private final RestrictionIntegrationEvents integrationEvents;
    private final OutboxRepository outbox;
    private final Clock clock;

    EventSourcedClientRestrictionRepository(JdbcEventStore eventStore, EventSerializer<RestrictionEvent> serializer,
                                            RestrictionIntegrationEvents integrationEvents, OutboxRepository outbox,
                                            Clock clock) {
        this.eventStore = eventStore;
        this.serializer = serializer;
        this.integrationEvents = integrationEvents;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public ClientRestriction findById(CustomerId customerId) {
        List<RestrictionEvent> history = eventStore.readStream(streamId(customerId)).stream()
                .map(serializer::deserialize)
                .toList();
        return ClientRestriction.rehydrate(customerId, history);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(ClientRestriction restriction) {
        List<RestrictionEvent> newEvents = restriction.uncommittedEvents();
        if (newEvents.isEmpty()) {
            return;
        }
        eventStore.append(streamId(restriction.customerId()), restriction.version(),
                newEvents.stream().map(serializer::toNewEvent).toList());
        integrationEvents.toOutboxMessages(restriction, newEvents)
                .forEach(message -> outbox.enqueue(message, clock.instant()));
        restriction.markEventsAsCommitted();
    }

    private static String streamId(CustomerId customerId) {
        return STREAM_PREFIX + customerId;
    }
}
