package com.example.onboarding.infrastructure.eventstore;

/** Another transaction appended to the same stream in the meantime - the command must be retried on fresh state. */
public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String streamId, long expectedVersion, Throwable cause) {
        super("Stream %s was modified concurrently (expected version %d)".formatted(streamId, expectedVersion), cause);
    }
}
