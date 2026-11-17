package com.gs5.restriction.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.kafka")
public record MessagingProperties(Topics topics,
                                  @DefaultValue("6") int partitions,
                                  @DefaultValue("1") short replicationFactor,
                                  @DefaultValue("1s") Duration retryInterval,
                                  @DefaultValue("3") long maxRetries) {

    /**
     * @param restrictionEvents  topic owned and written by this service
     * @param screeningEvents    topic owned by the screening service, consumed here
     * @param screeningEventsDlt dead letter topic of this service's consumer
     */
    public record Topics(String restrictionEvents, String screeningEvents, String screeningEventsDlt) {
    }
}
