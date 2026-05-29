package com.shop.auth;

import com.shop.auth.dto.AuthResponse;
import com.shop.auth.dto.LoginRequest;
import com.shop.auth.dto.RegisterRequest;
import com.shop.error.EmailAlreadyUsedException;
import com.shop.security.AppUserDetails;
import com.shop.security.JwtTokenProvider;
import com.shop.user.domain.Role;
import com.shop.user.domain.User;
import com.shop.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}: registration (happy path + duplicate email) and login
 * (happy path + bad credentials). Collaborators are mocked — no Spring context, no real DB.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerPersistsUserWithHashedPasswordAndDefaultRole() {
        when(userRepository.existsByEmail("new@shop.local")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("hashed-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User toSave = invocation.getArgument(0);
            toSave.setId(42L);
            return toSave;
        });

        Long id = authService.register(new RegisterRequest("New@Shop.local", "Secret123"));

        assertThat(id).isEqualTo(42L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("new@shop.local"); // normalised to lower-case
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-secret");
        assertThat(saved.getPasswordHash()).isNotEqualTo("Secret123"); // never store raw password
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@shop.local")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("taken@shop.local", "Secret123")))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        User user = new User();
        user.setId(7L);
        user.setEmail("user@shop.local");
        user.setPasswordHash("hashed");
        user.setRole(Role.USER);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AppUserDetails(user), null, new AppUserDetails(user).getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(tokenProvider.generateToken("user@shop.local", Role.USER)).thenReturn("jwt-token");
        when(tokenProvider.getTtlSeconds()).thenReturn(3600L);

        AuthResponse response = authService.login(new LoginRequest("User@Shop.local", "Secret123"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.email()).isEqualTo("user@shop.local");
        assertThat(response.role()).isEqualTo(Role.USER);
    }

    @Test
    void loginPropagatesAuthenticationFailure() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@shop.local", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verify(tokenProvider, never()).generateToken(any(), any());
    }
}
