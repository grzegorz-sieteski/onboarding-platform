package com.example.screening.infrastructure.config;

import com.example.screening.domain.ScreeningEvent;
import com.example.screening.infrastructure.eventstore.EventSerializer;
import com.example.screening.infrastructure.outbox.OutboxProperties;
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
    EventSerializer<ScreeningEvent> screeningEventSerializer(ObjectMapper objectMapper) {
        return new EventSerializer<>(objectMapper, ScreeningEvent.class);
    }
}
