package com.gs5.onboarding.domain;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every way an operation of the onboarding context can fail. Sealed: a {@code switch} over it is checked for
 * exhaustiveness, so adding a case forces every adapter (HTTP, Kafka, scheduler) to decide how to handle it.
 */
public sealed interface OnboardingError {

    String message();

    /** Input violates a business rule - repeating it gives the same answer. */
    record InvalidValue(String message) implements OnboardingError {
    }

    /** Several parts of the input are invalid at once - all of them are reported together. */
    record InvalidInput(List<OnboardingError> problems) implements OnboardingError {

        public InvalidInput {
            // a part that was itself built from several parts contributes its problems, not a nested group
            problems = problems.stream()
                    .flatMap(problem -> problem instanceof InvalidInput(var nested) ? nested.stream() : Stream.of(problem))
                    .toList();
        }

        @Override
        public String message() {
            return problems.stream().map(OnboardingError::message).collect(Collectors.joining("; "));
        }
    }

    record OnboardingNotFound(CustomerId customerId) implements OnboardingError {
        @Override
        public String message() {
            return "No onboarding found for customer " + customerId;
        }
    }

    record IllegalTransition(CustomerId customerId, OnboardingStatus from, String action) implements OnboardingError {
        @Override
        public String message() {
            return "Cannot %s onboarding of customer %s in status %s".formatted(action, customerId, from);
        }
    }

    /** Another writer changed the onboarding in the meantime - the command may be retried on fresh state. */
    record ConcurrentModification(CustomerId customerId, long expectedVersion) implements OnboardingError {
        @Override
        public String message() {
            return "Onboarding of customer %s was modified concurrently (expected version %d)"
                    .formatted(customerId, expectedVersion);
        }
    }

    /** The stored history cannot be read back - needs an operator, never retried automatically. */
    record CorruptedHistory(CustomerId customerId, String reason) implements OnboardingError {
        @Override
        public String message() {
            return "History of onboarding %s is corrupted: %s".formatted(customerId, reason);
        }
    }
}
