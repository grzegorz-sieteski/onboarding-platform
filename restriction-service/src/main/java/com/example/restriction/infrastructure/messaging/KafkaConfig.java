package com.example.restriction.infrastructure.messaging;

import com.example.restriction.domain.shared.DomainException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
class KafkaConfig {

    private static final int PARTITION_CHOSEN_BY_KEY = -1;

    /** Each service owns (creates) the topic it publishes to. */
    @Bean
    NewTopic restrictionEventsTopic(MessagingProperties properties) {
        return topic(properties.topics().restrictionEvents(), properties);
    }

    @Bean
    NewTopic screeningEventsDeadLetterTopic(MessagingProperties properties) {
        return topic(properties.topics().screeningEventsDlt(), properties);
    }

    /**
     * Blocking retries on the same partition, then dead letter topic.
     * Non-blocking retry topics are deliberately NOT used: they would let later events of the same customer
     * overtake the failing one and break ordering.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaOperations<String, String> kafka, MessagingProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka, (record, exception) ->
                new TopicPartition(properties.topics().screeningEventsDlt(), PARTITION_CHOSEN_BY_KEY));
        var errorHandler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(properties.retryInterval().toMillis(), properties.maxRetries()));
        errorHandler.addNotRetryableExceptions(MalformedMessageException.class, DomainException.class);
        return errorHandler;
    }

    private static NewTopic topic(String name, MessagingProperties properties) {
        return TopicBuilder.name(name)
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }
}
