package com.gs5.onboarding.application;

import com.gs5.onboarding.domain.CustomerId;

import java.time.Instant;
import java.util.List;

/** Port: finds onboardings whose screening deadline has passed (backed by a projection). */
public interface OverdueScreenings {

    List<CustomerId> dueBefore(Instant now, int limit);
}
