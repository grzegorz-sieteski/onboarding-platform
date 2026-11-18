package com.gs5.onboarding.application;

import com.gs5.onboarding.domain.CustomerId;
import com.gs5.onboarding.domain.OnboardingError;
import com.gs5.onboarding.domain.shared.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/**
 * Use case: escalate screenings that did not finish in time. Every onboarding is escalated in its own
 * transaction, so one failure (e.g. a concurrent "cleared" verdict) does not block the others; it is simply
 * picked up again on the next run - and the aggregate decides again whether escalation is still needed.
 */
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
                switch (commands.handle(new EscalateOverdueScreeningCommand(customerId))) {
                    case Result.Success<CustomerId, OnboardingError> _ ->
                            log.info("Screening of customer {} timed out - escalated to manual review", customerId);
                    case Result.Failure<CustomerId, OnboardingError>(var error) ->
                            log.warn("Escalation of customer {} failed, will retry on next run: {}",
                                    customerId, error.message());
                }
            } catch (RuntimeException e) {
                // infrastructure outage (database down): keep going with the rest of the batch
                log.warn("Escalation of customer {} failed, will retry on next run", customerId, e);
            }
        }
    }
}
