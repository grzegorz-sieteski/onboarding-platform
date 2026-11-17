package com.gs5.restriction.domain.shared;

/** Violation of a business rule. Never retried - repeating the same input yields the same violation. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
