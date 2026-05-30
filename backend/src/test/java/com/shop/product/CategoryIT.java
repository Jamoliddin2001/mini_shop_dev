package com.shop.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the public {@code GET /api/categories} endpoint against a real PostgreSQL
 * (Testcontainers): it returns the seeded categories ({@code V2__seed.sql}) as a flat list sorted
 * by name. The catalog filter UI relies on this contract; other ITs only used it as a helper.
 */
@AutoConfigureMockMvc
class CategoryIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listsSeededCategoriesSortedByName() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(body).hasSizeGreaterThanOrEqualTo(4); // V2 seeds 4 categories

        List<String> names = new ArrayList<>();
        body.forEach(node -> names.add(node.get("name").asText()));

        assertThat(names).contains("Books");

        // Endpoint returns Sort.by("name") — the list must already be in ascending order.
        List<String> ascending = new ArrayList<>(names);
        ascending.sort(Comparator.naturalOrder());
        assertThat(names).isEqualTo(ascending);
    }
}
