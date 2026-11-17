package com.gs5.onboarding.application;

import com.gs5.onboarding.domain.CustomerId;
import com.gs5.onboarding.domain.CustomerOnboardingRepository;
import com.gs5.onboarding.domain.OnboardingError;
import com.gs5.onboarding.domain.shared.Result;
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
    public Result<OnboardingView, OnboardingError> get(CustomerId customerId) {
        return onboardings.load(customerId).map(OnboardingView::of);
    }

    @Transactional(readOnly = true)
    public List<ManualReviewItem> pendingManualReviews() {
        return manualReviewQueue.pending();
    }
}
