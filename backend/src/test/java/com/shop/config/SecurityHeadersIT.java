package com.shop.config;

import com.shop.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the security response headers pinned in {@link SecurityConfig} are present on a public
 * endpoint, and that HSTS is absent in the default (non-prod) profile even over HTTPS. The matching
 * "HSTS present" case is covered by {@link SecurityHeadersHstsIT}.
 */
@AutoConfigureMockMvc
class SecurityHeadersIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicResponseCarriesHardenedHeaders() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    void hstsIsDisabledByDefaultEvenOverHttps() throws Exception {
        mockMvc.perform(get("/api/products").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
