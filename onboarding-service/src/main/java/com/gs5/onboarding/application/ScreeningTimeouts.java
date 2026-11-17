package com.gs5.onboarding.application;

import com.gs5.onboarding.domain.CustomerId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Use case: escalate screenings that did not finish in time. Every onboarding is escalated in its own
 * transaction, so one failure (e.g. a concurrent "cleared" verdict) does not block the others; it is simply
 * picked up again on the next run - and the aggregate decides again whether escalation is still needed.
 */
@Service
public class ScreeningTimeouts {

    private static final Logger log = LoggerFactory.getLogger(ScreeningTimeouts.class);

    private final OverdueScreenings overdueScreenings;
    private final OnboardingCommandHandler commands;
    private final Clock clock;

    public ScreeningTimeouts(OverdueScreenings overdueScreenings, OnboardingCommandHandler commands, Clock clock) {
        this.overdueScreenings = overdueScreenings;
        this.commands = commands;
        this.clock = clock;
    }

    public void escalateOverdue(int batchSize) {
        for (CustomerId customerId : overdueScreenings.dueBefore(clock.instant(), batchSize)) {
            try {
                commands.handle(new EscalateOverdueScreeningCommand(customerId));
                log.info("Screening of customer {} timed out - escalated to manual review", customerId);
            } catch (RuntimeException e) {
                log.warn("Escalation of customer {} failed, will retry on next run", customerId, e);
            }
        }
    }
}
