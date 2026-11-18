package com.gs5.screening.infrastructure.messaging;

import com.gs5.screening.application.ScreeningCommandHandler;
import com.gs5.screening.application.UnitOfWork;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/** Wiring of the Kafka adapter: topics, error handling, the published language and the inbound listener. */
@Configuration(proxyBeanMethods = false)
public class MessagingConfiguration {

    private static final int PARTITION_CHOSEN_BY_KEY = -1;

    @Bean
    MessagingProperties messagingProperties(Environment environment) {
        return Binder.get(environment).bind("app.kafka", MessagingProperties.class)
                .orElseThrow(() -> new IllegalStateException("Missing configuration: app.kafka"));
    }

    @Bean
    IntegrationEventCodec integrationEventCodec(ObjectMapper objectMapper) {
        return new IntegrationEventCodec(objectMapper);
    }

    @Bean
    ScreeningIntegrationEvents screeningIntegrationEvents(IntegrationEventCodec codec,
                                                          MessagingProperties properties) {
        return new ScreeningIntegrationEvents(codec, properties);
    }

    @Bean
    InboxProcessor inboxProcessor(JdbcClient jdbc, UnitOfWork unitOfWork, Clock clock) {
        return new InboxProcessor(jdbc, unitOfWork, clock);
    }

    /** Each service owns (creates) the topic it publishes to. */
    @Bean
    NewTopic screeningEventsTopic(MessagingProperties properties) {
        return topic(properties.topics().screeningEvents(), properties);
    }

    @Bean
    NewTopic onboardingEventsDeadLetterTopic(MessagingProperties properties) {
        return topic(properties.topics().onboardingEventsDlt(), properties);
    }

    /**
     * Blocking retries on the same partition, then dead letter topic.
     * Non-blocking retry topics are deliberately NOT used: they would let later events of the same customer
     * overtake the failing one and break ordering.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaOperations<String, String> kafka, MessagingProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka, (record, exception) ->
                new TopicPartition(properties.topics().onboardingEventsDlt(), PARTITION_CHOSEN_BY_KEY));
        var errorHandler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(properties.retryInterval().toMillis(), properties.maxRetries()));
        errorHandler.addNotRetryableExceptions(RejectedMessageException.class);
        return errorHandler;
    }

    /**
     * A listener container built by Spring Boot's factory, so it gets the error handler above and the ack mode and
     * concurrency from application.yml - everything an annotated listener would get, without the annotation.
     */
    @Bean
    ConcurrentMessageListenerContainer<String, String> onboardingEventsListener(
            ConcurrentKafkaListenerContainerFactory<String, String> containers, MessagingProperties properties,
            IntegrationEventCodec codec, InboxProcessor inbox, ScreeningCommandHandler commands) {
        var listener = new OnboardingEventsListener(codec, inbox, commands);
        var container = containers.createContainer(properties.topics().onboardingEvents());
        container.setupMessageListener((MessageListener<String, String>) record -> listener.on(record.value()));
        return container;
    }

    private static NewTopic topic(String name, MessagingProperties properties) {
        return TopicBuilder.name(name)
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }
}
