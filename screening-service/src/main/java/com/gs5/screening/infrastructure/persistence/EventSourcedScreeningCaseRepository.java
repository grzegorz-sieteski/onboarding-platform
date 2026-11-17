package com.gs5.screening.infrastructure.persistence;

import com.gs5.screening.domain.CustomerId;
import com.gs5.screening.domain.ScreeningCase;
import com.gs5.screening.domain.ScreeningCaseRepository;
import com.gs5.screening.domain.ScreeningError;
import com.gs5.screening.domain.ScreeningError.ConcurrentModification;
import com.gs5.screening.domain.ScreeningError.CorruptedHistory;
import com.gs5.screening.domain.ScreeningError.ScreeningNotFound;
import com.gs5.screening.domain.ScreeningEvent;
import com.gs5.screening.domain.shared.Result;
import com.gs5.screening.infrastructure.eventstore.EventSerializer;
import com.gs5.screening.infrastructure.eventstore.JdbcEventStore;
import com.gs5.screening.infrastructure.eventstore.StoredEvent;
import com.gs5.screening.infrastructure.eventstore.StreamConflict;
import com.gs5.screening.infrastructure.messaging.ScreeningIntegrationEvents;
import com.gs5.screening.infrastructure.outbox.OutboxRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

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
    public Result<ScreeningCase, ScreeningError> load(CustomerId customerId) {
        var history = new ArrayList<ScreeningEvent>();
        for (StoredEvent stored : eventStore.readStream(streamId(customerId))) {
            switch (serializer.deserialize(stored)) {
                case Result.Success(var event) -> history.add(event);
                case Result.Failure(var reason) -> {
                    return Result.failure(new CorruptedHistory(customerId, reason));
                }
            }
        }
        return Result.of(ScreeningCase.rehydrate(history), () -> new ScreeningNotFound(customerId));
    }

    @Override
    public boolean exists(CustomerId customerId) {
        return eventStore.streamExists(streamId(customerId));
    }

    /**
     * Events and outbox messages are written atomically, in one local transaction. A lost optimistic concurrency
     * race is reported as a {@code ConcurrentModification} value; anything else that goes wrong is an
     * infrastructure failure and surfaces as an exception, which rolls the whole transaction back.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result<ScreeningCase, ScreeningError> save(ScreeningCase screeningCase) {
        List<ScreeningEvent> newEvents = screeningCase.uncommittedEvents();
        if (newEvents.isEmpty()) {
            return Result.success(screeningCase);
        }
        return switch (eventStore.append(streamId(screeningCase.customerId()), screeningCase.version(),
                newEvents.stream().map(serializer::toNewEvent).toList())) {
            case Result.Failure<Void, StreamConflict>(var conflict) ->
                    Result.failure(new ConcurrentModification(screeningCase.customerId(), conflict.expectedVersion()));
            case Result.Success<Void, StreamConflict> _ -> {
                integrationEvents.toOutboxMessages(screeningCase, newEvents)
                        .forEach(message -> outbox.enqueue(message, clock.instant()));
                screeningCase.markEventsAsCommitted();
                yield Result.success(screeningCase);
            }
        };
    }

    private static String streamId(CustomerId customerId) {
        return STREAM_PREFIX + customerId;
    }
}
