package com.gs5.restriction;

import com.gs5.restriction.infrastructure.config.UseCaseConfiguration;
import com.gs5.restriction.infrastructure.messaging.MessagingConfiguration;
import com.gs5.restriction.infrastructure.outbox.OutboxConfiguration;
import com.gs5.restriction.infrastructure.persistence.PersistenceConfiguration;
import com.gs5.restriction.infrastructure.scheduling.SchedulingConfiguration;
import com.gs5.restriction.infrastructure.web.WebConfiguration;
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
public class RestrictionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestrictionServiceApplication.class, args);
    }
}
