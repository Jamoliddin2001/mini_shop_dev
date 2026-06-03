package com.shop.config;

import com.shop.security.JwtAuthenticationEntryPoint;
import com.shop.security.JwtAuthenticationFilter;
import com.shop.security.RestAccessDeniedHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Central security configuration (Spring-managed beans / Singleton).
 *
 * <p>Stateless JWT: no server session, CSRF disabled (no cookies), HTTP Basic / form login off.
 * Authentication is performed by the {@link JwtAuthenticationFilter}; 401/403 are rendered in the
 * unified {@code ApiError} format by the entry-point / access-denied handler.</p>
 *
 * <p>Method security is enabled so {@code @PreAuthorize("hasRole('ADMIN')")} can guard admin
 * methods in later phases. The ADMIN boundary for product-mutating routes is also pinned here at
 * the route level — it is the real rule the product phase relies on and gives a testable 403 now.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** BCrypt with the default strength (10). Must match the strength used to seed the admin hash. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   JwtAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   @Value("${security.headers.hsts-enabled:false}") boolean hstsEnabled)
            throws Exception {
        log.info("Configuring stateless JWT security: public=[POST /api/auth/**, GET /api/products/**, "
                + "GET /api/categories/**, /actuator/health, POST /graphql, /graphiql/**], "
                + "ADMIN=[write /api/products/**], "
                + "headers=[nosniff, frame-deny, no-referrer, CSP default-src 'none'], hsts={}", hstsEnabled);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // Explicit, testable security response headers (defense-in-depth). For a JSON-only API
                // a strict CSP is cheap and correct. HSTS is meaningful only over HTTPS, so it is
                // gated by a flag that is enabled only in the prod profile.
                .headers(headers -> headers
                        .contentTypeOptions(withDefaults())                       // X-Content-Type-Options: nosniff
                        .frameOptions(frame -> frame.deny())                       // X-Frame-Options: DENY
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .httpStrictTransportSecurity(hsts -> {
                            if (!hstsEnabled) {
                                hsts.disable();
                            }
                        }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public auth + actuator health
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // GraphQL transport: авторизация на уровне резолверов (@PreAuthorize)
                        .requestMatchers(HttpMethod.POST, "/graphql").permitAll()
                        .requestMatchers("/graphiql/**").permitAll()
                        // Public catalog reads
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
                        // Admin-only catalog writes (forward-compatible rule; enforced now)
                        .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                        // Everything else requires authentication
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
