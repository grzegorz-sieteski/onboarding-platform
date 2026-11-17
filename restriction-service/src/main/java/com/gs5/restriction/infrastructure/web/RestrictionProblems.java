package com.gs5.restriction.infrastructure.web;

import com.gs5.restriction.domain.RestrictionError;
import com.gs5.restriction.domain.RestrictionError.ConcurrentModification;
import com.gs5.restriction.domain.RestrictionError.CorruptedHistory;
import com.gs5.restriction.domain.RestrictionError.InvalidValue;
import com.gs5.restriction.domain.shared.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.function.Function;

/**
 * Turns the outcome of a use case into an HTTP response. Both switches are exhaustive over sealed types: a new
 * error that is not mapped here does not compile.
 */
final class RestrictionProblems {

    private RestrictionProblems() {
    }

    static <T> ResponseEntity<?> respond(Result<T, RestrictionError> result,
                                         Function<T, ResponseEntity<?>> onSuccess) {
        return switch (result) {
            case Result.Success<T, RestrictionError>(var value) -> onSuccess.apply(value);
            case Result.Failure<T, RestrictionError>(var error) -> {
                ProblemDetail problem = problemFor(error);
                yield ResponseEntity.status(problem.getStatus()).body(problem);
            }
        };
    }

    static ProblemDetail problemFor(RestrictionError error) {
        HttpStatus status = switch (error) {
            case InvalidValue _ -> HttpStatus.BAD_REQUEST;
            case ConcurrentModification _ -> HttpStatus.CONFLICT;
            case CorruptedHistory _ -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ProblemDetail.forStatusAndDetail(status, error.message());
    }
}
