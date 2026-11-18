package com.gs5.restriction.infrastructure.messaging;

import java.time.Duration;

/**
 * Configuration of the Kafka adapter, bound from {@code app.kafka}.
 *
 * @param retryInterval pause between blocking retries of a failed message
 * @param maxRetries    retries before a message goes to the dead letter topic
 */
public record MessagingProperties(Topics topics, int partitions, short replicationFactor, Duration retryInterval,
                                  long maxRetries) {

    /**
     * @param restrictionEvents  topic owned and written by this service
     * @param screeningEvents    topic owned by the screening service, consumed here
     * @param screeningEventsDlt dead letter topic of this service's consumer
     */
    public record Topics(String restrictionEvents, String screeningEvents, String screeningEventsDlt) {
    }
}
