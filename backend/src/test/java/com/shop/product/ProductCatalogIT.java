package com.shop.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.AbstractPostgresIT;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end catalog tests against a real PostgreSQL (Testcontainers): public listing + filters +
 * pagination, single lookup (200/404), admin CRUD, role enforcement (401/403), input validation,
 * bad query-parameter handling (400 not 500), and an N+1 guard via Hibernate statistics.
 *
 * <p>Seed data comes from {@code V2__seed.sql} (4 categories, 6 products); the admin user comes
 * from {@code V3__seed_admin.sql}. Each test leaves the DB at seed state (the CRUD lifecycle test
 * deletes what it creates), so listing/filter assertions can rely on the seeded rows.</p>
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ProductCatalogIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // ---------------------------------------------------------------- public reads / filters

    @Test
    void publicListReturnsPaginatedProducts() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6)));
    }

    @Test
    void filterByNameMatchesPartiallyCaseInsensitive() throws Exception {
        mockMvc.perform(get("/api/products").param("name", "clean"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Clean Code"));
    }

    @Test
    void filterByCategoryReturnsOnlyThatCategory() throws Exception {
        long booksId = categoryId("Books");

        MvcResult result = mockMvc.perform(get("/api/products").param("categoryId", String.valueOf(booksId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = body(result).get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
        for (JsonNode product : content) {
            assertThat(product.get("categoryId").asLong()).isEqualTo(booksId);
            assertThat(product.get("categoryName").asText()).isEqualTo("Books");
        }
    }

    @Test
    void filterByPriceRangeReturnsProductsWithinBounds() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products")
                        .param("minPrice", "30").param("maxPrice", "50"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = body(result).get("content");
        assertThat(content).isNotEmpty();
        for (JsonNode product : content) {
            double price = product.get("price").asDouble();
            assertThat(price).isBetween(30.0, 50.0);
        }
    }

    @Test
    void minPriceGreaterThanMaxPriceReturns400() throws Exception {
        // An inverted price range is rejected up front (cross-field rule the per-param constraints
        // cannot express) rather than silently querying for an impossible band.
        mockMvc.perform(get("/api/products").param("minPrice", "100").param("maxPrice", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void equalMinAndMaxPriceIsAllowed() throws Exception {
        // Boundary: min == max is a valid (degenerate) range, not an error.
        mockMvc.perform(get("/api/products").param("minPrice", "30").param("maxPrice", "30"))
                .andExpect(status().isOk());
    }

    @Test
    void getByIdReturnsProductThen404ForMissing() throws Exception {
        long id = firstProductId();
        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id));

        mockMvc.perform(get("/api/products/{id}", 9_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // ---------------------------------------------------------------- bad query parameters -> 400

    @Test
    void nonNumericPriceParamReturns400() throws Exception {
        mockMvc.perform(get("/api/products").param("minPrice", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void negativePriceParamReturns400() throws Exception {
        mockMvc.perform(get("/api/products").param("minPrice", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unknownSortPropertyReturns400() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---------------------------------------------------------------- roles / admin CRUD

    @Test
    void anonymousCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Anon", "19.99")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotCreateProduct() throws Exception {
        String token = userToken("catalog-user@shop.local");
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("UserProduct", "19.99")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminCanCreateUpdateAndDeleteProduct() throws Exception {
        String admin = adminToken();

        // create
        MvcResult created = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("IT Created Product", "9999.99")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("IT Created Product"))
                .andReturn();
        long id = body(created).get("id").asLong();

        // update
        mockMvc.perform(put("/api/products/{id}", id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("IT Updated Product", "8888.88")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("IT Updated Product"));

        // delete
        mockMvc.perform(delete("/api/products/{id}", id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        // gone
        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReferencingMissingCategoryReturns404() throws Exception {
        String admin = adminToken();
        String json = "{\"name\":\"BadCat\",\"price\":12.50,\"categoryId\":9999999}";
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void invalidCreatePayloadReturns400WithViolations() throws Exception {
        String admin = adminToken();
        // blank name, negative price, malformed URL
        String json = "{\"name\":\"\",\"price\":-5,\"imageUrl\":\"not-a-url\"}";
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.violations.length()")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    // ---------------------------------------------------------------- N+1 guard

    @Test
    void listingDoesNotTriggerNPlusOneOnCategory() throws Exception {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        mockMvc.perform(get("/api/products").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(6)));

        // Data query + count query (category is fetch-joined into the data query, not lazily
        // loaded per row). The count must be a small constant, NOT 1 + N (one per product).
        long statements = stats.getPrepareStatementCount();
        assertThat(statements)
                .as("listing should not issue one query per product (N+1)")
                .isLessThanOrEqualTo(3);
    }

    // ---------------------------------------------------------------- helpers

    private long categoryId(String name) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode category : body(result)) {
            if (category.get("name").asText().equals(name)) {
                return category.get("id").asLong();
            }
        }
        throw new IllegalStateException("Seed category not found: " + name);
    }

    private long firstProductId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products")).andExpect(status().isOk()).andReturn();
        return body(result).get("content").get(0).get("id").asLong();
    }

    private String adminToken() throws Exception {
        return login("admin@shop.local", "Admin123!");
    }

    /** Registers (idempotent-ish per unique email) a USER and returns its access token. */
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
        return body(result).get("accessToken").asText();
    }

    private static String productJson(String name, String price) {
        return "{\"name\":\"" + name + "\",\"description\":\"d\",\"price\":" + price
                + ",\"imageUrl\":\"https://example.com/x.jpg\"}";
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
