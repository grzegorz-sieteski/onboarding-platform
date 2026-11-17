package com.example.onboarding.infrastructure.scheduling;

import com.example.onboarding.application.ScreeningTimeouts;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Driving adapter: periodically triggers the escalation use case. One replica at a time (ShedLock). */
@Component
class ScreeningTimeoutJob {

    private final ScreeningTimeouts screeningTimeouts;
    private final ScreeningProperties properties;

    ScreeningTimeoutJob(ScreeningTimeouts screeningTimeouts, ScreeningProperties properties) {
        this.screeningTimeouts = screeningTimeouts;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.screening.check-interval-ms:30000}")
    @SchedulerLock(name = "screening-timeout-job", lockAtMostFor = "PT5M")
    void escalateOverdueScreenings() {
        screeningTimeouts.escalateOverdue(properties.batchSize());
    }
}
