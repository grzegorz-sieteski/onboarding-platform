package com.gs5.onboarding.infrastructure.messaging;

import java.time.Duration;
import java.util.List;

/**
 * Configuration of the Kafka adapter, bound from {@code app.kafka}.
 *
 * @param deadLetterSuffix  appended to a consumed topic to name its dead letter topic
 * @param retryInterval     pause between blocking retries of a failed message
 * @param maxRetries        retries before a message goes to the dead letter topic
 */
public record MessagingProperties(Topics topics, String deadLetterSuffix, int partitions, short replicationFactor,
                                  Duration retryInterval, long maxRetries) {

    /** Every consumed topic gets its own dead letter topic, owned by this service. */
    public String deadLetterTopicOf(String consumedTopic) {
        return consumedTopic + deadLetterSuffix;
    }

    /**
     * @param onboardingEvents  topic owned and written by this service
     * @param screeningEvents   topic owned by the screening service, consumed here ({@code ClientCleared})
     * @param restrictionEvents topic owned by the restriction service, consumed here ({@code ClientBanned})
     */
    public record Topics(String onboardingEvents, String screeningEvents, String restrictionEvents) {

        List<String> consumed() {
            return List.of(screeningEvents, restrictionEvents);
        }
    }
}
