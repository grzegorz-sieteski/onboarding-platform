package com.gs5.onboarding.infrastructure.outbox;

import com.gs5.onboarding.infrastructure.scheduling.PeriodicJob;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.time.Duration;

/** Wiring of the transactional outbox and of the job that relays it to Kafka. */
@Configuration(proxyBeanMethods = false)
public class OutboxConfiguration {

    @Bean
    OutboxProperties outboxProperties(Environment environment) {
        return Binder.get(environment).bind("app.outbox", OutboxProperties.class)
                .orElseThrow(() -> new IllegalStateException("Missing configuration: app.outbox"));
    }

    @Bean
    OutboxRepository outboxRepository(JdbcClient jdbc) {
        return new OutboxRepository(jdbc);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    PeriodicJob outboxRelayJob(OutboxRepository outbox, KafkaTemplate<String, String> kafka,
                               OutboxProperties properties, LockingTaskExecutor locks, Clock clock) {
        var relay = new OutboxRelay(outbox, kafka, properties, clock);
        return new PeriodicJob("outbox-relay", Duration.ofMillis(properties.pollIntervalMs()), Duration.ofMinutes(1),
                relay::relayPendingMessages, locks, clock);
    }
}
