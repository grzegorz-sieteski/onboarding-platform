package com.example.restriction.infrastructure.config;

import com.example.restriction.domain.RestrictionEvent;
import com.example.restriction.infrastructure.eventstore.EventSerializer;
import com.example.restriction.infrastructure.outbox.OutboxProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    EventSerializer<RestrictionEvent> restrictionEventSerializer(ObjectMapper objectMapper) {
        return new EventSerializer<>(objectMapper, RestrictionEvent.class);
    }
}
