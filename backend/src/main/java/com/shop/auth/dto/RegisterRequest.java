package com.shop.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Validated at the controller boundary ({@code @Valid}).
 *
 * <p>Password is capped at 72 chars because BCrypt silently truncates longer inputs.</p>
 *
 * @param email    user email (stored lower-cased)
 * @param password raw password (8..72 chars), hashed with BCrypt before persistence
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
