package com.gs5.onboarding.infrastructure.messaging;

import com.gs5.onboarding.application.OnboardingCommandHandler;
import com.gs5.onboarding.application.UnitOfWork;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Wiring of the Kafka adapter: topics, error handling, the published language and the inbound listeners. */
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
    OnboardingIntegrationEvents onboardingIntegrationEvents(IntegrationEventCodec codec,
                                                            MessagingProperties properties) {
        return new OnboardingIntegrationEvents(codec, properties);
    }

    @Bean
    InboxProcessor inboxProcessor(JdbcClient jdbc, UnitOfWork unitOfWork, Clock clock) {
        return new InboxProcessor(jdbc, unitOfWork, clock);
    }

    /** Each service owns (creates) the topic it publishes to, plus the dead letter topics of its consumers. */
    @Bean
    KafkaAdmin.NewTopics ownedTopics(MessagingProperties properties) {
        var published = topic(properties.topics().onboardingEvents(), properties);
        var deadLetters = properties.topics().consumed().stream()
                .map(consumed -> topic(properties.deadLetterTopicOf(consumed), properties));
        return new KafkaAdmin.NewTopics(Stream.concat(Stream.of(published), deadLetters).toArray(NewTopic[]::new));
    }

    /**
     * Blocking retries on the same partition, then dead letter topic.
     * Non-blocking retry topics are deliberately NOT used: they would let later events of the same customer
     * overtake the failing one and break ordering.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaOperations<String, String> kafka, MessagingProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka, (record, exception) ->
                new TopicPartition(properties.deadLetterTopicOf(record.topic()), PARTITION_CHOSEN_BY_KEY));
        var errorHandler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(properties.retryInterval().toMillis(), properties.maxRetries()));
        errorHandler.addNotRetryableExceptions(RejectedMessageException.class);
        return errorHandler;
    }

    @Bean
    ConcurrentMessageListenerContainer<String, String> screeningEventsListener(
            ConcurrentKafkaListenerContainerFactory<String, String> containers, MessagingProperties properties,
            IntegrationEventCodec codec, InboxProcessor inbox, OnboardingCommandHandler commands) {
        var listener = new ScreeningEventsListener(codec, inbox, commands);
        return listen(containers, properties.topics().screeningEvents(), listener::on);
    }

    @Bean
    ConcurrentMessageListenerContainer<String, String> restrictionEventsListener(
            ConcurrentKafkaListenerContainerFactory<String, String> containers, MessagingProperties properties,
            IntegrationEventCodec codec, InboxProcessor inbox, OnboardingCommandHandler commands) {
        var listener = new RestrictionEventsListener(codec, inbox, commands);
        return listen(containers, properties.topics().restrictionEvents(), listener::on);
    }

    /**
     * A listener container built by Spring Boot's factory, so it gets the error handler above and the ack mode and
     * concurrency from application.yml - everything an annotated listener would get, without the annotation.
     */
    private static ConcurrentMessageListenerContainer<String, String> listen(
            ConcurrentKafkaListenerContainerFactory<String, String> containers, String topic, Consumer<String> handler) {
        var container = containers.createContainer(topic);
        container.setupMessageListener((MessageListener<String, String>) record -> handler.accept(record.value()));
        return container;
    }

    private static NewTopic topic(String name, MessagingProperties properties) {
        return TopicBuilder.name(name)
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }
}
