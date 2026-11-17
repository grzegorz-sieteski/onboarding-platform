package com.gs5.onboarding.application;

import java.util.List;

/** Port: onboardings waiting for a compliance officer's decision, oldest first (backed by a projection). */
public interface ManualReviewQueue {

    List<ManualReviewItem> pending();
}
