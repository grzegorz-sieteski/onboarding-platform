package com.example.screening.infrastructure.web;

import com.example.screening.domain.ScreeningNotFoundException;
import com.example.screening.domain.shared.InvalidValueException;
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

    @ExceptionHandler(ScreeningNotFoundException.class)
    ProblemDetail notFound(ScreeningNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
