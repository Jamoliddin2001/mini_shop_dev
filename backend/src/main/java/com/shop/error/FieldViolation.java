package com.shop.error;

/**
 * A single field-level validation problem, surfaced inside {@link ApiError#violations()}.
 *
 * @param field   the offending request field
 * @param message human-readable validation message (never contains the submitted value)
 */
public record FieldViolation(String field, String message) {
}
