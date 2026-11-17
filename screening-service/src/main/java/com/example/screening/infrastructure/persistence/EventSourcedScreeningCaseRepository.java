package com.example.screening.infrastructure.persistence;

import com.example.screening.domain.CustomerId;
import com.example.screening.domain.ScreeningCase;
import com.example.screening.domain.ScreeningCaseRepository;
import com.example.screening.domain.ScreeningEvent;
import com.example.screening.infrastructure.eventstore.EventSerializer;
import com.example.screening.infrastructure.eventstore.JdbcEventStore;
import com.example.screening.infrastructure.messaging.ScreeningIntegrationEvents;
import com.example.screening.infrastructure.outbox.OutboxRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Repository
class EventSourcedScreeningCaseRepository implements ScreeningCaseRepository {

    private static final String STREAM_PREFIX = "screening-case-";

    private final JdbcEventStore eventStore;
    private final EventSerializer<ScreeningEvent> serializer;
    private final ScreeningIntegrationEvents integrationEvents;
    private final OutboxRepository outbox;
    private final Clock clock;

    EventSourcedScreeningCaseRepository(JdbcEventStore eventStore, EventSerializer<ScreeningEvent> serializer,
                                        ScreeningIntegrationEvents integrationEvents, OutboxRepository outbox,
                                        Clock clock) {
        this.eventStore = eventStore;
        this.serializer = serializer;
        this.integrationEvents = integrationEvents;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public Optional<ScreeningCase> findById(CustomerId customerId) {
        List<ScreeningEvent> history = eventStore.readStream(streamId(customerId)).stream()
                .map(serializer::deserialize)
                .toList();
        return history.isEmpty() ? Optional.empty() : Optional.of(ScreeningCase.rehydrate(history));
    }

    @Override
    public boolean exists(CustomerId customerId) {
        return eventStore.streamExists(streamId(customerId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(ScreeningCase screeningCase) {
        List<ScreeningEvent> newEvents = screeningCase.uncommittedEvents();
        if (newEvents.isEmpty()) {
            return;
        }
        eventStore.append(streamId(screeningCase.customerId()), screeningCase.version(),
                newEvents.stream().map(serializer::toNewEvent).toList());
        integrationEvents.toOutboxMessages(screeningCase, newEvents)
                .forEach(message -> outbox.enqueue(message, clock.instant()));
        screeningCase.markEventsAsCommitted();
    }

    private static String streamId(CustomerId customerId) {
        return STREAM_PREFIX + customerId;
    }
}
