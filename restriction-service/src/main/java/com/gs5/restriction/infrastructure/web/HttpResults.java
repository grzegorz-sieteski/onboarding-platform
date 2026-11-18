package com.gs5.restriction.infrastructure.web;

import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionError;
import com.gs5.restriction.domain.RestrictionError.ConcurrentModification;
import com.gs5.restriction.domain.RestrictionError.CorruptedHistory;
import com.gs5.restriction.domain.RestrictionError.InvalidInput;
import com.gs5.restriction.domain.RestrictionError.InvalidValue;
import com.gs5.restriction.domain.shared.Result;
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
    static <T> Result<T, RestrictionError> read(ServerRequest request, Class<T> type) {
        try {
            T body = request.body(type);
            return body == null ? unreadable(type) : Result.success(body);
        } catch (Exception _) {
            return unreadable(type);
        }
    }

    static Result<CustomerId, RestrictionError> customerId(ServerRequest request) {
        return CustomerId.of(request.pathVariable("customerId"));
    }

    static ServerResponse ok(Object body) {
        return ServerResponse.ok().body(body);
    }

    /** The response for a use case that failed: status and details come from the sealed error. */
    static ServerResponse problem(RestrictionError error) {
        ProblemDetail problem = problemFor(error);
        return ServerResponse.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    static ProblemDetail problemFor(RestrictionError error) {
        HttpStatus status = switch (error) {
            case InvalidValue _, InvalidInput _ -> HttpStatus.BAD_REQUEST;
            case ConcurrentModification _ -> HttpStatus.CONFLICT;
            case CorruptedHistory _ -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, error.message());
        if (error instanceof InvalidInput(var problems)) {
            problem.setProperty("problems", problems.stream().map(RestrictionError::message).toList());
        }
        return problem;
    }

    private static <T> Result<T, RestrictionError> unreadable(Class<T> type) {
        return Result.failure(new InvalidValue("Request body is not a valid " + type.getSimpleName()));
    }
}
