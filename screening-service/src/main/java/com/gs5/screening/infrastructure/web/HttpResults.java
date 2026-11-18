package com.gs5.screening.infrastructure.web;

import com.gs5.screening.domain.ScreeningError;
import com.gs5.screening.domain.ScreeningError.ConcurrentModification;
import com.gs5.screening.domain.ScreeningError.CorruptedHistory;
import com.gs5.screening.domain.ScreeningError.InvalidValue;
import com.gs5.screening.domain.ScreeningError.ScreeningNotFound;
import com.gs5.screening.domain.shared.Result;
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
    static <T> Result<T, ScreeningError> read(ServerRequest request, Class<T> type) {
        try {
            T body = request.body(type);
            return body == null ? unreadable(type) : Result.success(body);
        } catch (Exception _) {
            return unreadable(type);
        }
    }

    static <T> ServerResponse respond(Result<T, ScreeningError> result, Function<T, ServerResponse> onSuccess) {
        return switch (result) {
            case Result.Success<T, ScreeningError>(var value) -> onSuccess.apply(value);
            case Result.Failure<T, ScreeningError>(var error) -> {
                ProblemDetail problem = problemFor(error);
                yield ServerResponse.status(problem.getStatus())
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problem);
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

    private static <T> Result<T, ScreeningError> unreadable(Class<T> type) {
        return Result.failure(new InvalidValue("Request body is not a valid " + type.getSimpleName()));
    }
}
