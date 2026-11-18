package com.gs5.restriction.infrastructure.persistence;

import com.gs5.restriction.domain.ClientRestriction;
import com.gs5.restriction.domain.ClientRestrictionRepository;
import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionError;
import com.gs5.restriction.domain.RestrictionError.ConcurrentModification;
import com.gs5.restriction.domain.RestrictionError.CorruptedHistory;
import com.gs5.restriction.domain.RestrictionEvent;
import com.gs5.restriction.domain.shared.Result;
import com.gs5.restriction.infrastructure.eventstore.EventSerializer;
import com.gs5.restriction.infrastructure.eventstore.JdbcEventStore;
import com.gs5.restriction.infrastructure.eventstore.StoredEvent;
import com.gs5.restriction.infrastructure.eventstore.StreamConflict;
import com.gs5.restriction.infrastructure.messaging.RestrictionIntegrationEvents;
import com.gs5.restriction.infrastructure.outbox.OutboxRepository;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

class EventSourcedClientRestrictionRepository implements ClientRestrictionRepository {

    private static final String STREAM_PREFIX = "client-restriction-";

    private final JdbcEventStore eventStore;
    private final EventSerializer<RestrictionEvent> serializer;
    private final RestrictionIntegrationEvents integrationEvents;
    private final OutboxRepository outbox;
    private final Clock clock;
    private final TransactionOperations insideCallersTransaction;

    /**
     * @param insideCallersTransaction joins the caller's transaction and refuses to run without one: events and
     *                                 outbox messages are only atomic inside a single transaction
     */
    EventSourcedClientRestrictionRepository(JdbcEventStore eventStore, EventSerializer<RestrictionEvent> serializer,
                                            RestrictionIntegrationEvents integrationEvents, OutboxRepository outbox,
                                            Clock clock, TransactionOperations insideCallersTransaction) {
        this.eventStore = eventStore;
        this.serializer = serializer;
        this.integrationEvents = integrationEvents;
        this.outbox = outbox;
        this.clock = clock;
        this.insideCallersTransaction = insideCallersTransaction;
    }

    @Override
    public Result<ClientRestriction, RestrictionError> load(CustomerId customerId) {
        var history = new ArrayList<RestrictionEvent>();
        for (StoredEvent stored : eventStore.readStream(streamId(customerId))) {
            switch (serializer.deserialize(stored)) {
                case Result.Success(var event) -> history.add(event);
                case Result.Failure(var reason) -> {
                    return Result.failure(new CorruptedHistory(customerId, reason));
                }
            }
        }
        return Result.success(ClientRestriction.rehydrate(customerId, history));
    }

    /**
     * Events and outbox messages are written atomically, in the caller's transaction. A lost optimistic concurrency
     * race is reported as a {@code ConcurrentModification} value; anything else that goes wrong is an
     * infrastructure failure and surfaces as an exception, which rolls the whole transaction back.
     */
    @Override
    public Result<ClientRestriction, RestrictionError> save(ClientRestriction restriction) {
        return insideCallersTransaction.execute(_ -> append(restriction));
    }

    private Result<ClientRestriction, RestrictionError> append(ClientRestriction restriction) {
        List<RestrictionEvent> newEvents = restriction.uncommittedEvents();
        if (newEvents.isEmpty()) {
            return Result.success(restriction);
        }
        return switch (eventStore.append(streamId(restriction.customerId()), restriction.version(),
                newEvents.stream().map(serializer::toNewEvent).toList())) {
            case Result.Failure<Void, StreamConflict>(var conflict) ->
                    Result.failure(new ConcurrentModification(restriction.customerId(), conflict.expectedVersion()));
            case Result.Success<Void, StreamConflict> _ -> {
                integrationEvents.toOutboxMessages(restriction, newEvents)
                        .forEach(message -> outbox.enqueue(message, clock.instant()));
                restriction.markEventsAsCommitted();
                yield Result.success(restriction);
            }
        };
    }

    private static String streamId(CustomerId customerId) {
        return STREAM_PREFIX + customerId;
    }
}
