package com.shop.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Type-safe JWT configuration bound from {@code security.jwt.*}. All values come from the
 * environment (never hard-coded) — see {@code .env.example} / {@code application.yml}.
 *
 * <p>Validated at startup: a blank secret or missing TTL fails fast instead of booting with an
 * insecure default. The secret is a Base64-encoded HMAC key of at least 256 bits.</p>
 *
 * @param secret Base64-encoded signing key (>= 256 bits / 32 bytes after decoding)
 * @param ttl    access-token time-to-live (ISO-8601 duration, e.g. {@code PT1H})
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration ttl
) {
}
