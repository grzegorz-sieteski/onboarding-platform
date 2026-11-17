package com.gs5.onboarding.infrastructure.projection;

import com.gs5.onboarding.domain.CustomerOnboarding;
import com.gs5.onboarding.domain.OnboardingEvent;

import java.util.List;

/**
 * Inline projection: updated in the same transaction as the event store append, so it is always consistent
 * with the events. Projections only read events - they never decide anything.
 */
public interface OnboardingProjection {

    void project(CustomerOnboarding onboarding, List<OnboardingEvent> newEvents);
}
