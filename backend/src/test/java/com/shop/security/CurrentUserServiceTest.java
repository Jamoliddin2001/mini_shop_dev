package com.shop.security;

import com.shop.user.domain.Role;
import com.shop.user.domain.User;
import com.shop.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CurrentUserService#currentUserId()}: resolves the numeric id from the
 * authenticated principal's email, and throws {@link IllegalStateException} when there is no
 * authentication, when the token is not authenticated, or when the principal's email no longer
 * maps to a user row. The {@link SecurityContextHolder} is cleared after each test so
 * authentication never leaks between tests.
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CurrentUserService currentUserService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String email) {
        // 3-arg constructor (with authorities) marks the token as authenticated.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private static User user(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRole(Role.USER);
        return u;
    }

    @Test
    void resolvesIdOfAuthenticatedUser() {
        authenticateAs("user@shop.local");
        when(userRepository.findByEmail("user@shop.local"))
                .thenReturn(Optional.of(user(42L, "user@shop.local")));

        assertThat(currentUserService.currentUserId()).isEqualTo(42L);
    }

    @Test
    void throwsWhenNoAuthenticationInContext() {
        // Context left empty -> getAuthentication() is null.
        assertThatThrownBy(() -> currentUserService.currentUserId())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsWhenPrincipalEmailHasNoRow() {
        authenticateAs("vanished@shop.local");
        when(userRepository.findByEmail("vanished@shop.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currentUserService.currentUserId())
                .isInstanceOf(IllegalStateException.class);
    }
}
