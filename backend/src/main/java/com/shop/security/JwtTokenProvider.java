package com.shop.security;

import com.shop.config.JwtProperties;
import com.shop.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates stateless access tokens (jjwt 0.12.x).
 *
 * <p>Token shape: {@code sub} = user email, custom claim {@code role} = {@link Role} name,
 * signed with HMAC-SHA using the Base64 secret from {@link JwtProperties}. There is no refresh
 * token by design — compromise risk is bounded by the short TTL.</p>
 *
 * <p>The token value itself is NEVER logged.</p>
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final Duration ttl;

    public JwtTokenProvider(JwtProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.secret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.ttl = properties.ttl();
        log.info("JWT provider initialised. Access-token TTL={}", ttl);
    }

    /** Issues a signed access token for the given subject (email) and role. */
    public String generateToken(String email, Role role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);
        String token = Jwts.builder()
                .subject(email)
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
        log.debug("Issued access token for role={} (expires {})", role, expiry);
        return token;
    }

    /** Seconds until a freshly issued token expires — surfaced to clients as {@code expiresIn}. */
    public long getTtlSeconds() {
        return ttl.toSeconds();
    }

    /**
     * Parses and verifies a token. Returns the embedded principal on success, or empty when the
     * token is missing/expired/tampered. The reason is logged at DEBUG without the token value.
     */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Role role = Role.valueOf(claims.get(ROLE_CLAIM, String.class));
            return Optional.of(new AuthenticatedUser(claims.getSubject(), role));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Principal extracted from a verified token.
     *
     * @param email subject (user email)
     * @param role  authorization role
     */
    public record AuthenticatedUser(String email, Role role) {
    }
}
