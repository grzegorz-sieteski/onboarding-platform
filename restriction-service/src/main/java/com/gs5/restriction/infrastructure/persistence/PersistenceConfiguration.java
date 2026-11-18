package com.gs5.restriction.infrastructure.persistence;

import com.gs5.restriction.application.UnitOfWork;
import com.gs5.restriction.domain.ClientRestrictionRepository;
import com.gs5.restriction.domain.RestrictionEvent;
import com.gs5.restriction.infrastructure.eventstore.EventSerializer;
import com.gs5.restriction.infrastructure.eventstore.JdbcEventStore;
import com.gs5.restriction.infrastructure.messaging.RestrictionIntegrationEvents;
import com.gs5.restriction.infrastructure.outbox.OutboxRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

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
    EventSerializer<RestrictionEvent> restrictionEventSerializer(ObjectMapper objectMapper) {
        return new EventSerializer<>(objectMapper, RestrictionEvent.class);
    }

    @Bean
    ClientRestrictionRepository clientRestrictions(JdbcEventStore eventStore,
                                                   EventSerializer<RestrictionEvent> serializer,
                                                   RestrictionIntegrationEvents integrationEvents,
                                                   OutboxRepository outbox, Clock clock,
                                                   PlatformTransactionManager transactionManager) {
        var insideCallersTransaction = new TransactionTemplate(transactionManager);
        insideCallersTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
        return new EventSourcedClientRestrictionRepository(eventStore, serializer, integrationEvents, outbox, clock,
                insideCallersTransaction);
    }
}
