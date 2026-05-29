package com.shop;

import com.shop.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 smoke test: verifies the application boots against a real PostgreSQL,
 * Flyway applies all migrations, seed data is present, and Actuator health is UP.
 */
class SmokeIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void contextLoadsAndFlywayMigrationsApplied() {
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(migrations).isNotNull().isGreaterThanOrEqualTo(2);

        // All domain tables exist.
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_name IN " +
                        "('users','categories','products','cart','cart_items','orders','order_items')",
                Integer.class);
        assertThat(tables).isEqualTo(7);
    }

    @Test
    void seedDataIsPresent() {
        assertThat(productRepository.count()).isGreaterThan(0);
    }

    @Test
    void actuatorHealthIsUp() {
        assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
    }
}
