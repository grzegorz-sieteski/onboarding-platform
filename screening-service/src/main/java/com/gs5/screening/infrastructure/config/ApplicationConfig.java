package com.gs5.screening.infrastructure.config;

import com.gs5.screening.domain.ScreeningEvent;
import com.gs5.screening.infrastructure.eventstore.EventSerializer;
import com.gs5.screening.infrastructure.outbox.OutboxProperties;
import tools.jackson.databind.ObjectMapper;
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
