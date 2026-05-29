package com.shop.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login payload. Validated at the controller boundary ({@code @Valid}).
 *
 * @param email    user email
 * @param password raw password
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
