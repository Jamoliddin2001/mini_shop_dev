package com.shop.security;

import com.shop.user.domain.Role;
import com.shop.user.domain.User;
import com.shop.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AppUserDetailsService}: maps an existing {@link User} to a Spring Security
 * {@link UserDetails} (email as username, hash as password, {@code ROLE_*} authority) and rejects an
 * unknown email with {@link UsernameNotFoundException}. Collaborators are mocked — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppUserDetailsService appUserDetailsService;

    private static User user(String email, String hash, Role role) {
        User u = new User();
        u.setId(7L);
        u.setEmail(email);
        u.setPasswordHash(hash);
        u.setRole(role);
        return u;
    }

    @Test
    void loadsExistingUserAsUserDetails() {
        when(userRepository.findByEmail("user@shop.local"))
                .thenReturn(Optional.of(user("user@shop.local", "hashed", Role.ADMIN)));

        UserDetails details = appUserDetailsService.loadUserByUsername("user@shop.local");

        assertThat(details.getUsername()).isEqualTo("user@shop.local");
        assertThat(details.getPassword()).isEqualTo("hashed");
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void throwsWhenEmailNotFound() {
        when(userRepository.findByEmail("missing@shop.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserDetailsService.loadUserByUsername("missing@shop.local"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
