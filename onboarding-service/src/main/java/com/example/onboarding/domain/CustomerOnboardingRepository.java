package com.example.onboarding.domain;

import java.util.Optional;

/** Port: persistence of the aggregate. Implemented on top of the event store in the infrastructure layer. */
public interface CustomerOnboardingRepository {

    Optional<CustomerOnboarding> findById(CustomerId customerId);

    void save(CustomerOnboarding onboarding);
}
