package com.gs5.restriction.domain;

import com.gs5.restriction.domain.RestrictionError.InvalidValue;
import com.gs5.restriction.domain.shared.Result;

import java.util.UUID;
import java.util.regex.Pattern;

/** Identity of a customer - the same id is used as Kafka message key across all contexts. */
public record CustomerId(UUID value) {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public static CustomerId newId() {
        return new CustomerId(UUID.randomUUID());
    }

    /** Entry point for untrusted text (HTTP path, Kafka payload). The constructor is for already valid values. */
    public static Result<CustomerId, RestrictionError> of(String text) {
        if (text == null || !UUID_FORMAT.matcher(text).matches()) {
            return Result.failure(new InvalidValue("Invalid customer id: " + text));
        }
        return Result.success(new CustomerId(UUID.fromString(text)));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
