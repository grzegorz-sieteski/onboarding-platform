package com.gs5.onboarding.infrastructure.projection;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wiring of the inline projections. They stay package-private: the rest of the code sees only their ports. */
@Configuration(proxyBeanMethods = false)
public class ProjectionConfiguration {

    @Bean
    ManualReviewQueueProjection manualReviewQueue(JdbcClient jdbc) {
        return new ManualReviewQueueProjection(jdbc);
    }

    @Bean
    ScreeningDeadlineProjection screeningDeadlines(JdbcClient jdbc) {
        return new ScreeningDeadlineProjection(jdbc);
    }
}
