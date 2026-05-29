package com.shop.user;

import com.shop.user.domain.Role;
import com.shop.user.dto.MeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current-user endpoint. Requires authentication (enforced by SecurityConfig: any non-public
 * request must be authenticated). The principal and role come from the JWT.
 */
@RestController
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @GetMapping("/api/me")
    public MeResponse me(Authentication authentication) {
        String email = authentication.getName();
        Role role = extractRole(authentication);
        log.debug("GET /api/me for role={}", role);
        return new MeResponse(email, role);
    }

    private Role extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> Role.valueOf(authority.substring("ROLE_".length())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated principal has no role"));
    }
}
