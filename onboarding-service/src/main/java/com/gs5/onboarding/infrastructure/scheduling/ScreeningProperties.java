package com.gs5.onboarding.infrastructure.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param timeout          how long screening may take before escalation to manual review
 * @param checkIntervalMs  how often overdue screenings are looked for (escalation latency)
 * @param batchSize        max escalations per run
 */
@ConfigurationProperties(prefix = "app.screening")
public record ScreeningProperties(@DefaultValue("15m") Duration timeout,
                                  @DefaultValue("30000") long checkIntervalMs,
                                  @DefaultValue("100") int batchSize) {
}
