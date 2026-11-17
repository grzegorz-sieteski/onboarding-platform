package com.gs5.screening.infrastructure.web;

import com.gs5.screening.domain.ScreeningError;
import com.gs5.screening.domain.ScreeningError.ConcurrentModification;
import com.gs5.screening.domain.ScreeningError.CorruptedHistory;
import com.gs5.screening.domain.ScreeningError.InvalidValue;
import com.gs5.screening.domain.ScreeningError.ScreeningNotFound;
import com.gs5.screening.domain.shared.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.function.Function;

/**
 * Turns the outcome of a use case into an HTTP response. Both switches are exhaustive over sealed types: a new
 * error that is not mapped here does not compile.
 */
final class ScreeningProblems {

    private ScreeningProblems() {
    }

    static <T> ResponseEntity<?> respond(Result<T, ScreeningError> result,
                                         Function<T, ResponseEntity<?>> onSuccess) {
        return switch (result) {
            case Result.Success<T, ScreeningError>(var value) -> onSuccess.apply(value);
            case Result.Failure<T, ScreeningError>(var error) -> {
                ProblemDetail problem = problemFor(error);
                yield ResponseEntity.status(problem.getStatus()).body(problem);
            }
        };
    }

    static ProblemDetail problemFor(ScreeningError error) {
        HttpStatus status = switch (error) {
            case InvalidValue _ -> HttpStatus.BAD_REQUEST;
            case ScreeningNotFound _ -> HttpStatus.NOT_FOUND;
            case ConcurrentModification _ -> HttpStatus.CONFLICT;
            case CorruptedHistory _ -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ProblemDetail.forStatusAndDetail(status, error.message());
    }
}
