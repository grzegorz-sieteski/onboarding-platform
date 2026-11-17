package com.example.onboarding.application;

import com.example.onboarding.domain.CustomerId;
import com.example.onboarding.domain.CustomerOnboardingRepository;
import com.example.onboarding.domain.OnboardingNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read side. A single onboarding is built by replaying its (short) stream; lists that span many
 * aggregates, like the manual review queue, are served from projections.
 */
@Service
public class OnboardingQueries {

    private final CustomerOnboardingRepository onboardings;
    private final ManualReviewQueue manualReviewQueue;

    public OnboardingQueries(CustomerOnboardingRepository onboardings, ManualReviewQueue manualReviewQueue) {
        this.onboardings = onboardings;
        this.manualReviewQueue = manualReviewQueue;
    }

    @Transactional(readOnly = true)
    public OnboardingView get(CustomerId customerId) {
        return onboardings.findById(customerId)
                .map(OnboardingView::of)
                .orElseThrow(() -> new OnboardingNotFoundException(customerId));
    }

    @Transactional(readOnly = true)
    public List<ManualReviewItem> pendingManualReviews() {
        return manualReviewQueue.pending();
    }
}
