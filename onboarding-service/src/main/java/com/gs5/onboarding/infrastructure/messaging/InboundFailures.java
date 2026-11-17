package com.gs5.onboarding.infrastructure.messaging;

import com.gs5.onboarding.domain.OnboardingError;
import com.gs5.onboarding.domain.OnboardingError.ConcurrentModification;
import com.gs5.onboarding.domain.OnboardingError.CorruptedHistory;
import com.gs5.onboarding.domain.OnboardingError.IllegalTransition;
import com.gs5.onboarding.domain.OnboardingError.InvalidValue;
import com.gs5.onboarding.domain.OnboardingError.OnboardingNotFound;

/** Where a failed {@code Result} becomes the exception a Kafka listener has to throw. */
final class InboundFailures {

    private InboundFailures() {
    }

    /** The message itself is unusable: retrying cannot help. */
    static RuntimeException unusable(String reason) {
        return new RejectedMessageException(reason);
    }

    /** The message is fine but the domain refused it. Exhaustive: a new error forces a decision here. */
    static RuntimeException refused(OnboardingError error) {
        return switch (error) {
            case ConcurrentModification _ -> new RetryableMessageException(error.message());
            case InvalidValue _, OnboardingNotFound _, IllegalTransition _, CorruptedHistory _ ->
                    new RejectedMessageException(error.message());
        };
    }
}
