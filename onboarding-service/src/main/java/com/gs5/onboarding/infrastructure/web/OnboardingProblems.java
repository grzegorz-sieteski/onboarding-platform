package com.gs5.onboarding.infrastructure.web;

import com.gs5.onboarding.domain.OnboardingError;
import com.gs5.onboarding.domain.OnboardingError.ConcurrentModification;
import com.gs5.onboarding.domain.OnboardingError.CorruptedHistory;
import com.gs5.onboarding.domain.OnboardingError.IllegalTransition;
import com.gs5.onboarding.domain.OnboardingError.InvalidValue;
import com.gs5.onboarding.domain.OnboardingError.OnboardingNotFound;
import com.gs5.onboarding.domain.shared.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.function.Function;

/**
 * Turns the outcome of a use case into an HTTP response. Both switches are exhaustive over sealed types: a new
 * error that is not mapped here does not compile.
 */
final class OnboardingProblems {

    private OnboardingProblems() {
    }

    static <T> ResponseEntity<?> respond(Result<T, OnboardingError> result,
                                         Function<T, ResponseEntity<?>> onSuccess) {
        return switch (result) {
            case Result.Success<T, OnboardingError>(var value) -> onSuccess.apply(value);
            case Result.Failure<T, OnboardingError>(var error) -> {
                ProblemDetail problem = problemFor(error);
                yield ResponseEntity.status(problem.getStatus()).body(problem);
            }
        };
    }

    static ProblemDetail problemFor(OnboardingError error) {
        HttpStatus status = switch (error) {
            case InvalidValue _ -> HttpStatus.BAD_REQUEST;
            case OnboardingNotFound _ -> HttpStatus.NOT_FOUND;
            case IllegalTransition _, ConcurrentModification _ -> HttpStatus.CONFLICT;
            case CorruptedHistory _ -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ProblemDetail.forStatusAndDetail(status, error.message());
    }
}
