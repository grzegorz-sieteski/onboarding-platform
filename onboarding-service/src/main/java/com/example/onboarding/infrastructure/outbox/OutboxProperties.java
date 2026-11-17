package com.example.onboarding.infrastructure.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(@DefaultValue("100") int batchSize,
                               @DefaultValue("10s") Duration sendTimeout) {
}
