package com.example.onboarding.infrastructure.config;

import com.example.onboarding.domain.EligibilityPolicy;
import com.example.onboarding.domain.OnboardingEvent;
import com.example.onboarding.domain.ScreeningSla;
import com.example.onboarding.infrastructure.eventstore.EventSerializer;
import com.example.onboarding.infrastructure.outbox.OutboxProperties;
import com.example.onboarding.infrastructure.scheduling.ScreeningProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires framework-free domain objects into the Spring context. */
@Configuration
@EnableConfigurationProperties({OutboxProperties.class, ScreeningProperties.class})
class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    EligibilityPolicy eligibilityPolicy() {
        return new EligibilityPolicy();
    }

    @Bean
    ScreeningSla screeningSla(ScreeningProperties properties) {
        return new ScreeningSla(properties.timeout());
    }

    @Bean
    EventSerializer<OnboardingEvent> onboardingEventSerializer(ObjectMapper objectMapper) {
        return new EventSerializer<>(objectMapper, OnboardingEvent.class);
    }
}
