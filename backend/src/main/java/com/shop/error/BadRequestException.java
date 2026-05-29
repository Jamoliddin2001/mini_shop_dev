package com.shop.error;

/**
 * Thrown for client requests that are syntactically valid but semantically rejected by a
 * controller/service rule (e.g. sorting by a non-whitelisted field). Maps to HTTP 400 in
 * {@link GlobalExceptionHandler}.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
