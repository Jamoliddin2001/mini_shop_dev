package com.shop.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link JwtAuthenticationEntryPoint}: an unauthenticated hit on a protected route is
 * rendered as a 401 in the unified {@code ApiError} JSON shape (status/error/path), with a JSON
 * content type. Exercises the handler directly — these failures bypass {@code GlobalExceptionHandler}.
 */
class JwtAuthenticationEntryPointTest {

    // findAndRegisterModules() pulls in jackson-datatype-jsr310 so ApiError.timestamp (Instant)
    // serializes — mirroring the JSR-310-aware ObjectMapper Spring Boot auto-configures at runtime.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(objectMapper);

    @Test
    void writes401ApiErrorJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("path").asText()).isEqualTo("/api/cart");
    }
}
