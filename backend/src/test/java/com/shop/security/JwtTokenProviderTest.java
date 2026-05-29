package com.shop.security;

import com.shop.config.JwtProperties;
import com.shop.security.JwtTokenProvider.AuthenticatedUser;
import com.shop.user.domain.Role;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtTokenProvider}: generate/validate roundtrip plus rejection of
 * expired and tampered tokens. No Spring context — pure HMAC signing.
 */
class JwtTokenProviderTest {

    // Base64-encoded HMAC keys >= 256 bits, for tests only.
    private static final String SECRET =
            "dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItbWluaS1zaG9wLWludGVncmF0aW9uLXRlc3Rz";
    private static final String OTHER_SECRET =
            "YW5vdGhlci1qd3Qtc2VjcmV0LWtleS1mb3ItbWluaS1zaG9wLXVuaXQtdGVzdHMtMjAyNg==";

    private final JwtTokenProvider provider =
            new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofHours(1)));

    @Test
    void generateThenParseRoundtripsSubjectAndRole() {
        String token = provider.generateToken("user@shop.local", Role.USER);

        Optional<AuthenticatedUser> parsed = provider.parse(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().email()).isEqualTo("user@shop.local");
        assertThat(parsed.get().role()).isEqualTo(Role.USER);
    }

    @Test
    void ttlSecondsReflectsConfiguredDuration() {
        assertThat(provider.getTtlSeconds()).isEqualTo(3600L);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtTokenProvider expiringProvider =
                new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofSeconds(-1)));

        String expired = expiringProvider.generateToken("user@shop.local", Role.USER);

        assertThat(provider.parse(expired)).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        JwtTokenProvider otherProvider =
                new JwtTokenProvider(new JwtProperties(OTHER_SECRET, Duration.ofHours(1)));

        String foreignToken = otherProvider.generateToken("user@shop.local", Role.ADMIN);

        assertThat(provider.parse(foreignToken)).isEmpty();
    }

    @Test
    void malformedTokenIsRejected() {
        assertThat(provider.parse("not-a-jwt")).isEmpty();
    }
}
