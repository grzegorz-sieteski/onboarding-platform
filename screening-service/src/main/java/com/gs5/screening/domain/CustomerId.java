package com.gs5.screening.domain;

import com.gs5.screening.domain.shared.InvalidValueException;

import java.util.UUID;

/** Identity of a customer - the same id is used as Kafka message key across all contexts. */
public record CustomerId(UUID value) {

    public CustomerId {
        if (value == null) {
            throw new InvalidValueException("Customer id must not be null");
        }
    }

    public static CustomerId newId() {
        return new CustomerId(UUID.randomUUID());
    }

    public static CustomerId of(String value) {
        try {
            return new CustomerId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid customer id: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
