package com.shop.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Unified error response body returned for every 4xx/5xx (see {@link GlobalExceptionHandler}
 * and the security entry-point/handlers). The format is intentionally flat and stable so the
 * frontend can render errors uniformly. Stack traces are NEVER exposed here.
 *
 * @param timestamp  when the error was produced (UTC instant)
 * @param status     HTTP status code
 * @param error      short HTTP reason phrase (e.g. "Unauthorized")
 * @param message    safe, human-readable description (no secrets, no stack traces)
 * @param path       request path that produced the error
 * @param violations field-level validation problems (empty for non-validation errors)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> violations
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiError of(int status, String error, String message, String path, List<FieldViolation> violations) {
        return new ApiError(Instant.now(), status, error, message, path, violations);
    }
}
