package com.gs5.onboarding.infrastructure.persistence;

import com.gs5.onboarding.application.UnitOfWork;
import com.gs5.onboarding.domain.CustomerOnboardingRepository;
import com.gs5.onboarding.domain.OnboardingEvent;
import com.gs5.onboarding.infrastructure.eventstore.EventSerializer;
import com.gs5.onboarding.infrastructure.eventstore.JdbcEventStore;
import com.gs5.onboarding.infrastructure.messaging.OnboardingIntegrationEvents;
import com.gs5.onboarding.infrastructure.outbox.OutboxRepository;
import com.gs5.onboarding.infrastructure.projection.OnboardingProjection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;

/** Wiring of the persistence adapter: event store, event-sourced repository and the transaction boundary. */
@Configuration(proxyBeanMethods = false)
public class PersistenceConfiguration {

    @Bean
    UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
        return new TransactionTemplateUnitOfWork(transactionManager);
    }

    @Bean
    JdbcEventStore eventStore(JdbcClient jdbc) {
        return new JdbcEventStore(jdbc);
    }

    @Bean
    EventSerializer<OnboardingEvent> onboardingEventSerializer(ObjectMapper objectMapper) {
        return new EventSerializer<>(objectMapper, OnboardingEvent.class);
    }

    @Bean
    CustomerOnboardingRepository customerOnboardings(JdbcEventStore eventStore,
                                                     EventSerializer<OnboardingEvent> serializer,
                                                     OnboardingIntegrationEvents integrationEvents,
                                                     OutboxRepository outbox,
                                                     List<OnboardingProjection> projections,
                                                     Clock clock,
                                                     PlatformTransactionManager transactionManager) {
        var insideCallersTransaction = new TransactionTemplate(transactionManager);
        insideCallersTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
        return new EventSourcedCustomerOnboardingRepository(eventStore, serializer, integrationEvents, outbox,
                projections, clock, insideCallersTransaction);
    }
}
