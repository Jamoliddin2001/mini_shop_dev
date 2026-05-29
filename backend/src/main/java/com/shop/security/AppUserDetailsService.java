package com.shop.security;

import com.shop.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads users for authentication by email. Used by the {@code DaoAuthenticationProvider}
 * (login) — the JWT filter builds its principal straight from token claims and does not hit
 * the database on every request.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(AppUserDetailsService.class);

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading user for authentication");
        return userRepository.findByEmail(email)
                .map(AppUserDetails::new)
                .orElseThrow(() -> {
                    // Do not log the email (PII); the generic 401 also avoids account enumeration.
                    log.warn("Authentication attempt for an unknown user");
                    return new UsernameNotFoundException("User not found");
                });
    }
}
