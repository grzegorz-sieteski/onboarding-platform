package com.example.onboarding.infrastructure.projection;

import com.example.onboarding.application.ManualReviewItem;
import com.example.onboarding.application.ManualReviewQueue;
import com.example.onboarding.domain.CustomerOnboarding;
import com.example.onboarding.domain.OnboardingEvent;
import com.example.onboarding.domain.OnboardingEvent.CustomerOnboarded;
import com.example.onboarding.domain.OnboardingEvent.ManualReviewRequested;
import com.example.onboarding.domain.OnboardingEvent.OnboardingBanned;
import com.example.onboarding.domain.OnboardingEvent.OnboardingRejected;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Work queue of compliance officers: onboardings escalated to manual review and not yet decided. */
@Component
class ManualReviewQueueProjection implements OnboardingProjection, ManualReviewQueue {

    private final JdbcClient jdbc;

    ManualReviewQueueProjection(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void project(CustomerOnboarding onboarding, List<OnboardingEvent> newEvents) {
        for (OnboardingEvent event : newEvents) {
            if (event instanceof ManualReviewRequested requested) {
                enqueue(onboarding, requested);
            } else if (resolvesReview(event)) {
                remove(event.customerId());
            }
        }
    }

    @Override
    public List<ManualReviewItem> pending() {
        return jdbc.sql("""
                        SELECT customer_id, first_name, last_name, reason, requested_at
                          FROM manual_review_queue
                         ORDER BY requested_at
                        """)
                .query((rs, rowNum) -> new ManualReviewItem(
                        rs.getObject("customer_id", UUID.class).toString(),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("reason"),
                        rs.getObject("requested_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    private static boolean resolvesReview(OnboardingEvent event) {
        return event instanceof CustomerOnboarded
                || event instanceof OnboardingRejected
                || event instanceof OnboardingBanned;
    }

    private void enqueue(CustomerOnboarding onboarding, ManualReviewRequested requested) {
        jdbc.sql("""
                        INSERT INTO manual_review_queue (customer_id, first_name, last_name, reason, requested_at)
                        VALUES (:customerId, :firstName, :lastName, :reason, :requestedAt)
                        """)
                .param("customerId", requested.customerId())
                .param("firstName", onboarding.applicant().name().firstName())
                .param("lastName", onboarding.applicant().name().lastName())
                .param("reason", requested.reason())
                .param("requestedAt", OffsetDateTime.ofInstant(requested.occurredAt(), ZoneOffset.UTC))
                .update();
    }

    private void remove(UUID customerId) {
        jdbc.sql("DELETE FROM manual_review_queue WHERE customer_id = :customerId")
                .param("customerId", customerId)
                .update();
    }
}
