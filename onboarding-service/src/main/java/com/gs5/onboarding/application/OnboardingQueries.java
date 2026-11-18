package com.gs5.onboarding.application;

import com.gs5.onboarding.domain.CustomerId;
import com.gs5.onboarding.domain.CustomerOnboardingRepository;
import com.gs5.onboarding.domain.OnboardingError;
import com.gs5.onboarding.domain.shared.Result;

import java.util.List;

/**
 * Read side. A single onboarding is built by replaying its (short) stream; lists that span many
 * aggregates, like the manual review queue, are served from projections.
 */
public class OnboardingQueries {

    private final CustomerOnboardingRepository onboardings;
    private final ManualReviewQueue manualReviewQueue;
    private final UnitOfWork unitOfWork;

    public OnboardingQueries(CustomerOnboardingRepository onboardings, ManualReviewQueue manualReviewQueue,
                             UnitOfWork unitOfWork) {
        this.onboardings = onboardings;
        this.manualReviewQueue = manualReviewQueue;
        this.unitOfWork = unitOfWork;
    }

    public Result<OnboardingView, OnboardingError> get(CustomerId customerId) {
        return unitOfWork.readOnly(() -> onboardings.load(customerId).map(OnboardingView::of));
    }

    public List<ManualReviewItem> pendingManualReviews() {
        return unitOfWork.readOnly(manualReviewQueue::pending);
    }
}
