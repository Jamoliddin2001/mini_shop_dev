package com.shop;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that need a real PostgreSQL.
 * The container is wired into Spring via {@link ServiceConnection}, so no manual
 * datasource property plumbing is required. Flyway runs against this container.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractPostgresIT {

    /**
     * Deterministic Base64 HMAC key (>= 256 bits) for tests only — the real secret is supplied
     * via the {@code JWT_SECRET} environment variable in dev/prod. Never reuse this value.
     */
    protected static final String TEST_JWT_SECRET =
            "dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItbWluaS1zaG9wLWludGVncmF0aW9uLXRlc3Rz";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.ttl", () -> "PT1H");
    }
}
