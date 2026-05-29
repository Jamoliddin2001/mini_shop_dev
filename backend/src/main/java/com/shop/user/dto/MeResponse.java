package com.shop.user.dto;

import com.shop.user.domain.Role;

/**
 * Current-user projection returned by {@code GET /api/me}. Built from the JWT claims, so it
 * needs no database round-trip.
 *
 * @param email authenticated user email
 * @param role  authenticated user role
 */
public record MeResponse(String email, Role role) {
}
