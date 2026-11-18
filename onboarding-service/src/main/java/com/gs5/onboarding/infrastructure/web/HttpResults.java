package com.gs5.onboarding.infrastructure.web;

import com.gs5.onboarding.domain.OnboardingError;
import com.gs5.onboarding.domain.OnboardingError.ConcurrentModification;
import com.gs5.onboarding.domain.OnboardingError.CorruptedHistory;
import com.gs5.onboarding.domain.OnboardingError.IllegalTransition;
import com.gs5.onboarding.domain.OnboardingError.InvalidInput;
import com.gs5.onboarding.domain.OnboardingError.InvalidValue;
import com.gs5.onboarding.domain.OnboardingError.OnboardingNotFound;
import com.gs5.onboarding.domain.shared.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.function.Function;

/**
 * Translation between HTTP and the {@code Result} of a use case. Both switches are exhaustive over sealed types:
 * an error that is not mapped here does not compile.
 */
final class HttpResults {

    private HttpResults() {
    }

    /** A body that cannot be read is invalid input like any other; validation itself belongs to the domain. */
    static <T> Result<T, OnboardingError> read(ServerRequest request, Class<T> type) {
        try {
            T body = request.body(type);
            return body == null ? unreadable(type) : Result.success(body);
        } catch (Exception _) {
            return unreadable(type);
        }
    }

    static <T> ServerResponse respond(Result<T, OnboardingError> result, Function<T, ServerResponse> onSuccess) {
        return switch (result) {
            case Result.Success<T, OnboardingError>(var value) -> onSuccess.apply(value);
            case Result.Failure<T, OnboardingError>(var error) -> {
                ProblemDetail problem = problemFor(error);
                yield ServerResponse.status(problem.getStatus())
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problem);
            }
        };
    }

    static ProblemDetail problemFor(OnboardingError error) {
        HttpStatus status = switch (error) {
            case InvalidValue _, InvalidInput _ -> HttpStatus.BAD_REQUEST;
            case OnboardingNotFound _ -> HttpStatus.NOT_FOUND;
            case IllegalTransition _, ConcurrentModification _ -> HttpStatus.CONFLICT;
            case CorruptedHistory _ -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, error.message());
        if (error instanceof InvalidInput(var problems)) {
            problem.setProperty("problems", problems.stream().map(OnboardingError::message).toList());
        }
        return problem;
    }

    private static <T> Result<T, OnboardingError> unreadable(Class<T> type) {
        return Result.failure(new InvalidValue("Request body is not a valid " + type.getSimpleName()));
    }
}
