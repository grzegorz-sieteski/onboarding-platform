package com.gs5.screening.infrastructure.persistence;

import com.gs5.screening.application.UnitOfWork;
import com.gs5.screening.domain.Blacklist;
import com.gs5.screening.domain.ScreeningCaseRepository;
import com.gs5.screening.domain.ScreeningEvent;
import com.gs5.screening.infrastructure.eventstore.EventSerializer;
import com.gs5.screening.infrastructure.eventstore.JdbcEventStore;
import com.gs5.screening.infrastructure.messaging.ScreeningIntegrationEvents;
import com.gs5.screening.infrastructure.outbox.OutboxRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/** Wiring of the persistence adapter: event store, repositories and the transaction boundary. */
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
    EventSerializer<ScreeningEvent> screeningEventSerializer(ObjectMapper objectMapper) {
        return new EventSerializer<>(objectMapper, ScreeningEvent.class);
    }

    @Bean
    ScreeningCaseRepository screeningCases(JdbcEventStore eventStore, EventSerializer<ScreeningEvent> serializer,
                                           ScreeningIntegrationEvents integrationEvents, OutboxRepository outbox,
                                           Clock clock, PlatformTransactionManager transactionManager) {
        var insideCallersTransaction = new TransactionTemplate(transactionManager);
        insideCallersTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
        return new EventSourcedScreeningCaseRepository(eventStore, serializer, integrationEvents, outbox, clock,
                insideCallersTransaction);
    }

    @Bean
    Blacklist blacklist(JdbcClient jdbc, Clock clock) {
        return new JdbcBlacklist(jdbc, clock);
    }
}
