package com.gs5.restriction.infrastructure.messaging;

/** The message cannot be understood - retrying will not help, it goes straight to the dead letter topic. */
public class MalformedMessageException extends RuntimeException {

    public MalformedMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
