package com.gs5.onboarding.infrastructure.persistence;

import com.gs5.onboarding.domain.CustomerId;
import com.gs5.onboarding.domain.CustomerOnboarding;
import com.gs5.onboarding.domain.CustomerOnboardingRepository;
import com.gs5.onboarding.domain.OnboardingError;
import com.gs5.onboarding.domain.OnboardingError.ConcurrentModification;
import com.gs5.onboarding.domain.OnboardingError.CorruptedHistory;
import com.gs5.onboarding.domain.OnboardingError.OnboardingNotFound;
import com.gs5.onboarding.domain.OnboardingEvent;
import com.gs5.onboarding.domain.shared.Result;
import com.gs5.onboarding.infrastructure.eventstore.EventSerializer;
import com.gs5.onboarding.infrastructure.eventstore.JdbcEventStore;
import com.gs5.onboarding.infrastructure.eventstore.StoredEvent;
import com.gs5.onboarding.infrastructure.eventstore.StreamConflict;
import com.gs5.onboarding.infrastructure.messaging.OnboardingIntegrationEvents;
import com.gs5.onboarding.infrastructure.outbox.OutboxRepository;
import com.gs5.onboarding.infrastructure.projection.OnboardingProjection;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Repository
class EventSourcedCustomerOnboardingRepository implements CustomerOnboardingRepository {

    private static final String STREAM_PREFIX = "customer-onboarding-";

    private final JdbcEventStore eventStore;
    private final EventSerializer<OnboardingEvent> serializer;
    private final OnboardingIntegrationEvents integrationEvents;
    private final OutboxRepository outbox;
    private final List<OnboardingProjection> projections;
    private final Clock clock;

    EventSourcedCustomerOnboardingRepository(JdbcEventStore eventStore, EventSerializer<OnboardingEvent> serializer,
                                             OnboardingIntegrationEvents integrationEvents, OutboxRepository outbox,
                                             List<OnboardingProjection> projections, Clock clock) {
        this.eventStore = eventStore;
        this.serializer = serializer;
        this.integrationEvents = integrationEvents;
        this.outbox = outbox;
        this.projections = projections;
        this.clock = clock;
    }

    @Override
    public Result<CustomerOnboarding, OnboardingError> load(CustomerId customerId) {
        var history = new ArrayList<OnboardingEvent>();
        for (StoredEvent stored : eventStore.readStream(streamId(customerId))) {
            switch (serializer.deserialize(stored)) {
                case Result.Success(var event) -> history.add(event);
                case Result.Failure(var reason) -> {
                    return Result.failure(new CorruptedHistory(customerId, reason));
                }
            }
        }
        return Result.of(CustomerOnboarding.rehydrate(history), () -> new OnboardingNotFound(customerId));
    }

    /**
     * Events, inline projections and outbox messages are written atomically, in one local transaction.
     * <p>
     * Optimistic concurrency conflict is reported as a {@code ConcurrentModification} value. Anything else that goes
     * wrong - the projection or the outbox insert, a lost connection - is an infrastructure failure and surfaces as
     * an exception, which rolls the whole transaction back: events, projections and outbox messages go together.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result<CustomerOnboarding, OnboardingError> save(CustomerOnboarding onboarding) {
        List<OnboardingEvent> newEvents = onboarding.uncommittedEvents();
        if (newEvents.isEmpty()) {
            return Result.success(onboarding);
        }
        return switch (eventStore.append(streamId(onboarding.customerId()), onboarding.version(),
                newEvents.stream().map(serializer::toNewEvent).toList())) {
            case Result.Failure<Void, StreamConflict>(var conflict) ->
                    Result.failure(new ConcurrentModification(onboarding.customerId(), conflict.expectedVersion()));
            case Result.Success<Void, StreamConflict> _ -> {
                projections.forEach(projection -> projection.project(onboarding, newEvents));
                integrationEvents.toOutboxMessages(onboarding, newEvents)
                        .forEach(message -> outbox.enqueue(message, clock.instant()));
                onboarding.markEventsAsCommitted();
                yield Result.success(onboarding);
            }
        };
    }

    private static String streamId(CustomerId customerId) {
        return STREAM_PREFIX + customerId;
    }
}
