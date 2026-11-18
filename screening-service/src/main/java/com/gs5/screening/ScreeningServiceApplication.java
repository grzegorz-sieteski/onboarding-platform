package com.gs5.screening;

import com.gs5.screening.infrastructure.config.UseCaseConfiguration;
import com.gs5.screening.infrastructure.messaging.MessagingConfiguration;
import com.gs5.screening.infrastructure.outbox.OutboxConfiguration;
import com.gs5.screening.infrastructure.persistence.PersistenceConfiguration;
import com.gs5.screening.infrastructure.scheduling.SchedulingConfiguration;
import com.gs5.screening.infrastructure.web.WebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Composition root. Nothing is found by classpath scanning: the application is exactly the configurations listed
 * here - one per adapter, each building plain objects with {@code new}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        UseCaseConfiguration.class,
        PersistenceConfiguration.class,
        OutboxConfiguration.class,
        MessagingConfiguration.class,
        SchedulingConfiguration.class,
        WebConfiguration.class})
public class ScreeningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScreeningServiceApplication.class, args);
    }
}
