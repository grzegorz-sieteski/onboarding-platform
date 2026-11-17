package com.gs5.onboarding.infrastructure.persistence;

import com.gs5.onboarding.domain.CustomerId;
import com.gs5.onboarding.domain.CustomerOnboarding;
import com.gs5.onboarding.domain.CustomerOnboardingRepository;
import com.gs5.onboarding.domain.OnboardingEvent;
import com.gs5.onboarding.infrastructure.eventstore.EventSerializer;
import com.gs5.onboarding.infrastructure.eventstore.JdbcEventStore;
import com.gs5.onboarding.infrastructure.messaging.OnboardingIntegrationEvents;
import com.gs5.onboarding.infrastructure.outbox.OutboxRepository;
import com.gs5.onboarding.infrastructure.projection.OnboardingProjection;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

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
    public Optional<CustomerOnboarding> findById(CustomerId customerId) {
        List<OnboardingEvent> history = eventStore.readStream(streamId(customerId)).stream()
                .map(serializer::deserialize)
                .toList();
        return history.isEmpty() ? Optional.empty() : Optional.of(CustomerOnboarding.rehydrate(history));
    }

    /** Events, inline projections and outbox messages are written atomically, in one local transaction. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(CustomerOnboarding onboarding) {
        List<OnboardingEvent> newEvents = onboarding.uncommittedEvents();
        if (newEvents.isEmpty()) {
            return;
        }
        eventStore.append(streamId(onboarding.customerId()), onboarding.version(),
                newEvents.stream().map(serializer::toNewEvent).toList());
        projections.forEach(projection -> projection.project(onboarding, newEvents));
        integrationEvents.toOutboxMessages(onboarding, newEvents)
                .forEach(message -> outbox.enqueue(message, clock.instant()));
        onboarding.markEventsAsCommitted();
    }

    private static String streamId(CustomerId customerId) {
        return STREAM_PREFIX + customerId;
    }
}
