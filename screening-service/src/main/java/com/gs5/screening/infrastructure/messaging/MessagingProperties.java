package com.gs5.screening.infrastructure.messaging;

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
     * @param screeningEvents     topic owned and written by this service
     * @param onboardingEvents    topic owned by the onboarding service, consumed here
     * @param onboardingEventsDlt dead letter topic of this service's consumer
     */
    public record Topics(String screeningEvents, String onboardingEvents, String onboardingEventsDlt) {
    }
}
