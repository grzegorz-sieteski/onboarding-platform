package com.example.screening.domain.shared;

public class InvalidValueException extends DomainException {

    public InvalidValueException(String message) {
        super(message);
    }
}
