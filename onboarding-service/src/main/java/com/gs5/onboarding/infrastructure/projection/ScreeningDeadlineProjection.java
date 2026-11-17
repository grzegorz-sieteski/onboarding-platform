package com.gs5.onboarding.infrastructure.projection;

import com.gs5.onboarding.application.OverdueScreenings;
import com.gs5.onboarding.domain.CustomerId;
import com.gs5.onboarding.domain.CustomerOnboarding;
import com.gs5.onboarding.domain.OnboardingEvent;
import com.gs5.onboarding.domain.OnboardingEvent.ApplicantVerified;
import com.gs5.onboarding.domain.OnboardingEvent.CustomerOnboarded;
import com.gs5.onboarding.domain.OnboardingEvent.ManualReviewRequested;
import com.gs5.onboarding.domain.OnboardingEvent.OnboardingBanned;
import com.gs5.onboarding.domain.OnboardingEvent.OnboardingRejected;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Open screening windows: a row lives from {@code ApplicantVerified} until the screening is decided or escalated. */
@Component
class ScreeningDeadlineProjection implements OnboardingProjection, OverdueScreenings {

    private final JdbcClient jdbc;

    ScreeningDeadlineProjection(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void project(CustomerOnboarding onboarding, List<OnboardingEvent> newEvents) {
        for (OnboardingEvent event : newEvents) {
            if (event instanceof ApplicantVerified verified) {
                open(verified.customerId(), verified.screeningDueAt());
            } else if (closesScreeningWindow(event)) {
                close(event.customerId());
            }
        }
    }

    @Override
    public List<CustomerId> dueBefore(Instant now, int limit) {
        return jdbc.sql("""
                        SELECT customer_id FROM screening_deadline
                         WHERE due_at <= :now
                         ORDER BY due_at
                         LIMIT :limit
                        """)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("limit", limit)
                .query((rs, rowNum) -> new CustomerId(rs.getObject("customer_id", UUID.class)))
                .list();
    }

    private static boolean closesScreeningWindow(OnboardingEvent event) {
        return event instanceof ManualReviewRequested
                || event instanceof CustomerOnboarded
                || event instanceof OnboardingRejected
                || event instanceof OnboardingBanned;
    }

    private void open(UUID customerId, Instant dueAt) {
        jdbc.sql("INSERT INTO screening_deadline (customer_id, due_at) VALUES (:customerId, :dueAt)")
                .param("customerId", customerId)
                .param("dueAt", OffsetDateTime.ofInstant(dueAt, ZoneOffset.UTC))
                .update();
    }

    private void close(UUID customerId) {
        jdbc.sql("DELETE FROM screening_deadline WHERE customer_id = :customerId")
                .param("customerId", customerId)
                .update();
    }
}
