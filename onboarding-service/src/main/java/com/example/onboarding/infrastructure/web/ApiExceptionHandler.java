package com.example.onboarding.infrastructure.web;

import com.example.onboarding.domain.IllegalOnboardingTransitionException;
import com.example.onboarding.domain.OnboardingNotFoundException;
import com.example.onboarding.domain.shared.InvalidValueException;
import com.example.onboarding.infrastructure.eventstore.ConcurrencyConflictException;
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

    @ExceptionHandler(OnboardingNotFoundException.class)
    ProblemDetail notFound(OnboardingNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalOnboardingTransitionException.class)
    ProblemDetail illegalTransition(IllegalOnboardingTransitionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    ProblemDetail conflict(ConcurrencyConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
