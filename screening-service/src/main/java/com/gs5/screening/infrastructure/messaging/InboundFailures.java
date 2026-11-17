package com.gs5.screening.infrastructure.messaging;

import com.gs5.screening.domain.ScreeningError;
import com.gs5.screening.domain.ScreeningError.ConcurrentModification;
import com.gs5.screening.domain.ScreeningError.CorruptedHistory;
import com.gs5.screening.domain.ScreeningError.InvalidValue;
import com.gs5.screening.domain.ScreeningError.ScreeningNotFound;

/** Where a failed {@code Result} becomes the exception a Kafka listener has to throw. */
final class InboundFailures {

    private InboundFailures() {
    }

    /** The message itself is unusable: retrying cannot help. */
    static RuntimeException unusable(String reason) {
        return new RejectedMessageException(reason);
    }

    /** The message is fine but the domain refused it. Exhaustive: a new error forces a decision here. */
    static RuntimeException refused(ScreeningError error) {
        return switch (error) {
            case ConcurrentModification _ -> new RetryableMessageException(error.message());
            case InvalidValue _, ScreeningNotFound _, CorruptedHistory _ ->
                    new RejectedMessageException(error.message());
        };
    }
}
