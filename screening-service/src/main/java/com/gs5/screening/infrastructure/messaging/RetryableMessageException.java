package com.gs5.screening.infrastructure.messaging;

/**
 * Like {@link RejectedMessageException}, but for a failure that may be gone on the next attempt (a concurrent
 * writer won the race): the container retries the message with a back-off before it dead-letters it.
 */
class RetryableMessageException extends RuntimeException {

    RetryableMessageException(String reason) {
        super(reason);
    }
}
