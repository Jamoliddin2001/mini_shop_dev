package com.shop.security;

import com.shop.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Resolves the id of the currently authenticated user.
 *
 * <p>The stateless JWT filter intentionally does not hit the database per request, so the
 * security principal is just the user's email (the token's {@code sub}). Endpoints that operate on
 * user-owned data (cart, orders) need the numeric user id; this support service performs the one
 * indexed lookup by email (unique index {@code uq_users_email_idx}) and centralizes it so
 * controllers stay thin and do not depend on the user repository directly.</p>
 */
@Service
public class CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @return the authenticated user's id
     * @throws IllegalStateException if there is no authentication or the principal has no matching
     *                               user row (should never happen for a verified token)
     */
    public Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in the security context");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
