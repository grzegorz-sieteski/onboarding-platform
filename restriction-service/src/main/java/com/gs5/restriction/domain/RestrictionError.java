package com.gs5.restriction.domain;

/**
 * Every way an operation of the restriction context can fail. Sealed: a {@code switch} over it is checked for
 * exhaustiveness, so adding a case forces every adapter (HTTP, Kafka) to decide how to handle it.
 * There is no "not found": a client without history is simply unrestricted.
 */
public sealed interface RestrictionError {

    String message();

    /** Input violates a business rule - repeating it gives the same answer. */
    record InvalidValue(String message) implements RestrictionError {
    }

    /** Another writer changed the restriction in the meantime - the command may be retried on fresh state. */
    record ConcurrentModification(CustomerId customerId, long expectedVersion) implements RestrictionError {
        @Override
        public String message() {
            return "Restriction of customer %s was modified concurrently (expected version %d)"
                    .formatted(customerId, expectedVersion);
        }
    }

    /** The stored history cannot be read back - needs an operator, never retried automatically. */
    record CorruptedHistory(CustomerId customerId, String reason) implements RestrictionError {
        @Override
        public String message() {
            return "History of restriction %s is corrupted: %s".formatted(customerId, reason);
        }
    }
}
