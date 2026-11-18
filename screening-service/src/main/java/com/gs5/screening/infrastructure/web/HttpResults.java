package com.gs5.screening.infrastructure.web;

import com.gs5.screening.domain.CustomerId;
import com.gs5.screening.domain.ScreeningError;
import com.gs5.screening.domain.ScreeningError.ConcurrentModification;
import com.gs5.screening.domain.ScreeningError.CorruptedHistory;
import com.gs5.screening.domain.ScreeningError.InvalidInput;
import com.gs5.screening.domain.ScreeningError.InvalidValue;
import com.gs5.screening.domain.ScreeningError.ScreeningNotFound;
import com.gs5.screening.domain.shared.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Translation between HTTP and the use cases: request parts in, responses out. The switch over the sealed error is
 * exhaustive - an error that is not mapped here does not compile.
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

    static Result<CustomerId, ScreeningError> customerId(ServerRequest request) {
        return CustomerId.of(request.pathVariable("customerId"));
    }

    static ServerResponse ok(Object body) {
        return ServerResponse.ok().body(body);
    }

    /** The response for a use case that failed: status and details come from the sealed error. */
    static ServerResponse problem(ScreeningError error) {
        ProblemDetail problem = problemFor(error);
        return ServerResponse.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    static ProblemDetail problemFor(ScreeningError error) {
        HttpStatus status = switch (error) {
            case InvalidValue _, InvalidInput _ -> HttpStatus.BAD_REQUEST;
            case ScreeningNotFound _ -> HttpStatus.NOT_FOUND;
            case ConcurrentModification _ -> HttpStatus.CONFLICT;
            case CorruptedHistory _ -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, error.message());
        if (error instanceof InvalidInput(var problems)) {
            problem.setProperty("problems", problems.stream().map(ScreeningError::message).toList());
        }
        return problem;
    }

    private static <T> Result<T, ScreeningError> unreadable(Class<T> type) {
        return Result.failure(new InvalidValue("Request body is not a valid " + type.getSimpleName()));
    }
}
