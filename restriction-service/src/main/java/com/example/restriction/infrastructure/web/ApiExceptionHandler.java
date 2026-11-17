package com.example.restriction.infrastructure.web;

import com.example.restriction.domain.shared.InvalidValueException;
import com.example.restriction.infrastructure.eventstore.ConcurrencyConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InvalidValueException.class)
    ProblemDetail invalidValue(InvalidValueException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    ProblemDetail conflict(ConcurrencyConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
