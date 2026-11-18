package com.gs5.onboarding;

import com.gs5.onboarding.infrastructure.config.UseCaseConfiguration;
import com.gs5.onboarding.infrastructure.messaging.MessagingConfiguration;
import com.gs5.onboarding.infrastructure.outbox.OutboxConfiguration;
import com.gs5.onboarding.infrastructure.persistence.PersistenceConfiguration;
import com.gs5.onboarding.infrastructure.projection.ProjectionConfiguration;
import com.gs5.onboarding.infrastructure.scheduling.SchedulingConfiguration;
import com.gs5.onboarding.infrastructure.web.WebConfiguration;
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
        ProjectionConfiguration.class,
        OutboxConfiguration.class,
        MessagingConfiguration.class,
        SchedulingConfiguration.class,
        WebConfiguration.class})
public class OnboardingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnboardingServiceApplication.class, args);
    }
}
