package com.shop.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link RestAccessDeniedHandler}: an authenticated-but-unauthorized hit is rendered
 * as a 403 in the unified {@code ApiError} JSON shape (status/error/path), with a JSON content type.
 */
class RestAccessDeniedHandlerTest {

    // findAndRegisterModules() pulls in jackson-datatype-jsr310 so ApiError.timestamp (Instant)
    // serializes — mirroring the JSR-310-aware ObjectMapper Spring Boot auto-configures at runtime.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RestAccessDeniedHandler handler = new RestAccessDeniedHandler(objectMapper);

    @Test
    void writes403ApiErrorJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("error").asText()).isEqualTo("Forbidden");
        assertThat(body.get("path").asText()).isEqualTo("/api/products");
    }
}
