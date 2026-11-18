package com.gs5.onboarding.infrastructure.scheduling;

import com.gs5.onboarding.application.ScreeningTimeouts;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;

/** Wiring of the periodic jobs and of the distributed lock that keeps each of them on one replica at a time. */
@Configuration(proxyBeanMethods = false)
public class SchedulingConfiguration {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    @Bean
    LockingTaskExecutor lockingTaskExecutor(LockProvider lockProvider) {
        return new DefaultLockingTaskExecutor(lockProvider);
    }

    @Bean
    ScreeningProperties screeningProperties(Environment environment) {
        return Binder.get(environment).bind("app.screening", ScreeningProperties.class)
                .orElseThrow(() -> new IllegalStateException("Missing configuration: app.screening"));
    }

    /** Driving adapter: periodically triggers the escalation use case. */
    @Bean(initMethod = "start", destroyMethod = "stop")
    PeriodicJob screeningTimeoutJob(ScreeningTimeouts screeningTimeouts, ScreeningProperties properties,
                                    LockingTaskExecutor locks, Clock clock) {
        return new PeriodicJob("screening-timeout-job", Duration.ofMillis(properties.checkIntervalMs()),
                Duration.ofMinutes(5), () -> screeningTimeouts.escalateOverdue(properties.batchSize()), locks, clock);
    }
}
