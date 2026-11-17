package com.gs5.restriction.infrastructure.config;

import com.gs5.restriction.domain.RestrictionEvent;
import com.gs5.restriction.infrastructure.eventstore.EventSerializer;
import com.gs5.restriction.infrastructure.outbox.OutboxProperties;
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
    EventSerializer<RestrictionEvent> restrictionEventSerializer(ObjectMapper objectMapper) {
        return new EventSerializer<>(objectMapper, RestrictionEvent.class);
    }
}
