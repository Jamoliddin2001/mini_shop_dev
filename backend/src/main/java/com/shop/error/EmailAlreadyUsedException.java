package com.shop.error;

/**
 * Thrown when registration is attempted with an email that already exists.
 * Maps to HTTP 409 in {@link GlobalExceptionHandler}.
 *
 * <p>The message is intentionally generic and does NOT echo the submitted email, to keep
 * the address out of logs.</p>
 */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("Email is already registered");
    }
}
