package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.shared.Result;

/** Port: persistence of the aggregate. Implemented on top of the event store in the infrastructure layer. */
public interface CustomerOnboardingRepository {

    /**
     * Rebuilds the aggregate from its history. Fails with {@code OnboardingNotFound} when the customer has no
     * onboarding and with {@code CorruptedHistory} when a stored event cannot be read back.
     */
    Result<CustomerOnboarding, OnboardingError> load(CustomerId customerId);

    /**
     * Persists the uncommitted events atomically with their projections and outbox messages. A failure means
     * nothing was written; the caller must then roll its transaction back (the database may have aborted it).
     */
    Result<CustomerOnboarding, OnboardingError> save(CustomerOnboarding onboarding);
}
