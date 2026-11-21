package com.qnt.safedrive.claims;

/** The message can never be processed (bad JSON, wrong contract). Goes to the dead-letter topic without retries. */
public class UnprocessableEventException extends RuntimeException {

    public UnprocessableEventException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnprocessableEventException(String message) {
        super(message);
    }
}
