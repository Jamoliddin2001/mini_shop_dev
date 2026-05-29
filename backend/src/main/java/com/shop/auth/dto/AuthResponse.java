package com.shop.auth.dto;

import com.shop.user.domain.Role;

/**
 * Successful login response carrying the access token and the authenticated principal.
 *
 * @param accessToken signed JWT access token
 * @param tokenType   token scheme (always {@code "Bearer"})
 * @param expiresIn   token lifetime in seconds
 * @param email       authenticated user email
 * @param role        authenticated user role
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String email,
        Role role
) {

    public static AuthResponse bearer(String accessToken, long expiresIn, String email, Role role) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, email, role);
    }
}
