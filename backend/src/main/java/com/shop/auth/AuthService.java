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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Authentication use-cases (service layer). The controller stays thin; all business rules live
 * here. Passwords, hashes and tokens are never logged.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Registers a new user with the default {@link Role#USER} role.
     *
     * @return the id of the created user
     * @throws EmailAlreadyUsedException if the email is already registered
     */
    @Transactional
    public Long register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            log.debug("Registration rejected: email already exists");
            throw new EmailAlreadyUsedException();
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        User saved = userRepository.save(user);

        log.info("Registered new user id={} role={}", saved.getId(), saved.getRole());
        return saved.getId();
    }

    /**
     * Authenticates the credentials and issues an access token.
     *
     * @throws org.springframework.security.core.AuthenticationException on bad credentials
     */
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        String token = tokenProvider.generateToken(principal.getUsername(), principal.getRole());

        log.info("Login success for user id={} role={}", principal.getId(), principal.getRole());
        return AuthResponse.bearer(token, tokenProvider.getTtlSeconds(),
                principal.getUsername(), principal.getRole());
    }

    /** Emails are stored and compared lower-cased (DB enforces {@code email = lower(email)}). */
    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
