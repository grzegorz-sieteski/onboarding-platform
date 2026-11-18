package com.gs5.restriction.infrastructure.messaging;

import com.gs5.restriction.domain.RestrictionError;
import com.gs5.restriction.domain.RestrictionError.ConcurrentModification;
import com.gs5.restriction.domain.RestrictionError.CorruptedHistory;
import com.gs5.restriction.domain.RestrictionError.InvalidInput;
import com.gs5.restriction.domain.RestrictionError.InvalidValue;

/** Where a failed {@code Result} becomes the exception a Kafka listener has to throw. */
final class InboundFailures {

    private InboundFailures() {
    }

    /** The message itself is unusable: retrying cannot help. */
    static RuntimeException unusable(String reason) {
        return new RejectedMessageException(reason);
    }

    /** The message is fine but the domain refused it. Exhaustive: a new error forces a decision here. */
    static RuntimeException refused(RestrictionError error) {
        return switch (error) {
            case ConcurrentModification _ -> new RetryableMessageException(error.message());
            case InvalidValue _, InvalidInput _, CorruptedHistory _ -> new RejectedMessageException(error.message());
        };
    }
}
