package com.shop.product.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end GraphQL tests against a real PostgreSQL (Testcontainers).
 *
 * <p>Uses raw MockMvc POST requests to {@code /graphql} — the standard approach for Spring MVC
 * (non-reactive) applications. The GraphQL wire format is a JSON object with a {@code query}
 * and optional {@code variables} field.</p>
 *
 * <p>Seed data: V2__seed.sql (products + categories), V3__seed_admin.sql (admin@shop.local /
 * Admin123!). Mutation tests create uniquely-named products and clean up after themselves.</p>
 */
@AutoConfigureMockMvc
class ProductGraphQLIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── Query: product ───────────────────────────────────────────────────────

    @Test
    void queryProduct_returnsProduct_whenExists() throws Exception {
        // Get any valid id from the seed catalog via products query
        JsonNode productsData = graphql("{ products { content { id name } } }", null, null);
        long id = productsData.at("/products/content/0/id").asLong();
        assertThat(id).isPositive();

        JsonNode data = graphql("query($id: ID!) { product(id: $id) { id name price } }",
                Map.of("id", id), null);

        assertThat(data.at("/product/id").asLong()).isEqualTo(id);
        assertThat(data.at("/product/name").asText()).isNotBlank();
        assertThat(data.at("/product/price").asDouble()).isPositive();
    }

    @Test
    void queryProduct_returnsNotFoundError_whenMissing() throws Exception {
        JsonNode response = graphqlRaw("{ product(id: 999999) { id } }", null, null);

        assertThat(response.has("errors")).isTrue();
        assertThat(response.at("/errors/0/extensions/classification").asText())
                .isEqualTo("NOT_FOUND");
    }

    // ─── Query: products ──────────────────────────────────────────────────────

    @Test
    void queryProducts_returnsPagedResults() throws Exception {
        JsonNode data = graphql(
                "{ products(page: 0, size: 5) { content { id name } page size totalElements } }",
                null, null);

        assertThat(data.at("/products/page").asInt()).isZero();
        assertThat(data.at("/products/size").asInt()).isEqualTo(5);
        assertThat(data.at("/products/totalElements").asLong()).isGreaterThanOrEqualTo(1L);
        assertThat(data.at("/products/content").isArray()).isTrue();
        assertThat(data.at("/products/content").size()).isGreaterThan(0);
    }

    @Test
    void queryProducts_withNameFilter_returnsMatchingProducts() throws Exception {
        // V2__seed.sql contains "Clean Code" — partial ILIKE match
        JsonNode data = graphql(
                "query($f: ProductFilterInput!) { products(filter: $f) { content { name } } }",
                Map.of("f", Map.of("name", "clean")),
                null);

        String firstName = data.at("/products/content/0/name").asText();
        assertThat(firstName).containsIgnoringCase("clean");
    }

    // ─── Mutation: createProduct ──────────────────────────────────────────────

    @Test
    void mutationCreateProduct_withoutToken_returnsError() throws Exception {
        JsonNode response = graphqlRaw(
                "mutation { createProduct(input: {name: \"X\", price: 1.0}) { id } }",
                null, null);

        assertThat(response.has("errors")).isTrue();
        assertThat(response.at("/errors").size()).isGreaterThan(0);
    }

    @Test
    void mutationCreateProduct_withAdminToken_createsAndReturnsProduct() throws Exception {
        String token = adminToken();

        JsonNode data = graphql("""
                mutation($input: CreateProductInput!) {
                    createProduct(input: $input) { id name price }
                }
                """,
                Map.of("input", Map.of("name", "GraphQL Test Product", "price", 49.99)),
                token);

        assertThat(data.at("/createProduct/name").asText()).isEqualTo("GraphQL Test Product");
        assertThat(data.at("/createProduct/price").asDouble()).isPositive();

        long createdId = data.at("/createProduct/id").asLong();
        assertThat(createdId).isPositive();

        // Clean up so seed catalog is unchanged for other tests
        JsonNode deleteData = graphql(
                "mutation($id: ID!) { deleteProduct(id: $id) }",
                Map.of("id", createdId),
                token);
        assertThat(deleteData.at("/deleteProduct").asBoolean()).isTrue();
    }

    @Test
    void mutationDeleteProduct_withUserToken_returnsError() throws Exception {
        String userToken = userToken("gql-user-" + System.currentTimeMillis() + "@shop.local");

        JsonNode response = graphqlRaw("mutation { deleteProduct(id: 1) }", null, userToken);

        assertThat(response.has("errors")).isTrue();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Executes a GraphQL operation and returns the {@code data} node.
     * Asserts the response has no {@code errors}.
     */
    private JsonNode graphql(String query, Map<String, ?> variables, String token) throws Exception {
        JsonNode response = graphqlRaw(query, variables, token);
        assertThat(response.has("errors"))
                .as("Unexpected GraphQL errors: %s", response.path("errors"))
                .isFalse();
        return response.get("data");
    }

    /** Executes a GraphQL operation and returns the raw response (data + errors). */
    private JsonNode graphqlRaw(String query, Map<String, ?> variables, String token) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("query", query);
        if (variables != null) {
            body.put("variables", variables);
        }
        String json = objectMapper.writeValueAsString(body);

        var request = post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String adminToken() throws Exception {
        return login("admin@shop.local", "Admin123!");
    }

    private String userToken(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Secret123\"}"));
        return login(email, "Secret123");
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
