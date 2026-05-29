package com.shop.config;

import com.shop.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that when {@code security.headers.hsts-enabled=true} (the prod setting), an HTTPS request
 * receives the {@code Strict-Transport-Security} header. The default (disabled) case lives in
 * {@link SecurityHeadersIT}.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "security.headers.hsts-enabled=true")
class SecurityHeadersHstsIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void hstsHeaderIsSentOverHttpsWhenEnabled() throws Exception {
        mockMvc.perform(get("/api/products").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=")));
    }
}
