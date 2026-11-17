package com.gs5.onboarding.infrastructure.messaging;

/**
 * The only way to tell the Kafka listener container that a message failed. It is thrown at the very edge of a
 * listener, from a failed {@code Result}. Retrying cannot help, so the container sends the message straight to
 * the dead letter topic.
 */
class RejectedMessageException extends RuntimeException {

    RejectedMessageException(String reason) {
        super(reason);
    }
}
