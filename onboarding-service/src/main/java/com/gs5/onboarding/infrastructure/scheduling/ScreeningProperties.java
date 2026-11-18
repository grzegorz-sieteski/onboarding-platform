package com.gs5.onboarding.infrastructure.scheduling;

import java.time.Duration;

/**
 * Configuration of the screening deadline, bound from {@code app.screening}.
 *
 * @param timeout          how long screening may take before escalation to manual review
 * @param checkIntervalMs  how often overdue screenings are looked for (escalation latency)
 * @param batchSize        max escalations per run
 */
public record ScreeningProperties(Duration timeout, long checkIntervalMs, int batchSize) {
}
