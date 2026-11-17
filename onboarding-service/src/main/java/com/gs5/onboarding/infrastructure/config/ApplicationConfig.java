package com.gs5.onboarding.infrastructure.config;

import com.gs5.onboarding.domain.EligibilityPolicy;
import com.gs5.onboarding.domain.OnboardingEvent;
import com.gs5.onboarding.domain.ScreeningSla;
import com.gs5.onboarding.infrastructure.eventstore.EventSerializer;
import com.gs5.onboarding.infrastructure.outbox.OutboxProperties;
import com.gs5.onboarding.infrastructure.scheduling.ScreeningProperties;
import tools.jackson.databind.ObjectMapper;
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
        // a bad timeout in the configuration is a startup failure, not a business outcome
        return ScreeningSla.of(properties.timeout())
                .orElseThrow(error -> new IllegalStateException("app.screening.timeout: " + error.message()));
    }

    @Bean
    EventSerializer<OnboardingEvent> onboardingEventSerializer(ObjectMapper objectMapper) {
        return new EventSerializer<>(objectMapper, OnboardingEvent.class);
    }
}
