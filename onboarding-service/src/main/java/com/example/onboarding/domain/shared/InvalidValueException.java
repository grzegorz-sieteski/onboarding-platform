package com.example.onboarding.domain.shared;

public class InvalidValueException extends DomainException {

    public InvalidValueException(String message) {
        super(message);
    }
}
