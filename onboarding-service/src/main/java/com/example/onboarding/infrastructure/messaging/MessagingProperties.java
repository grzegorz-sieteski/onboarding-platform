package com.example.onboarding.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.kafka")
public record MessagingProperties(Topics topics,
                                  @DefaultValue(".onboarding-service.dlt") String deadLetterSuffix,
                                  @DefaultValue("6") int partitions,
                                  @DefaultValue("1") short replicationFactor,
                                  @DefaultValue("1s") Duration retryInterval,
                                  @DefaultValue("3") long maxRetries) {

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
