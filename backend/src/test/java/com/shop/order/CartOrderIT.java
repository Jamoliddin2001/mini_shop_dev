package com.shop.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.AbstractPostgresIT;
import com.shop.error.BadRequestException;
import com.shop.order.event.OrderCreatedEvent;
import com.shop.order.event.OrderCreatedListener;
import com.shop.order.repository.OrderRepository;
import com.shop.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end cart + order tests against a real PostgreSQL (Testcontainers): the add→checkout
 * lifecycle, the price snapshot guarantee, empty-cart and deleted-product edge cases, concurrent
 * checkout serialization, role/ownership enforcement, and the async OrderCreated event.
 *
 * <p>Each test registers its own uniquely-named user and creates its own uniquely-named products
 * (as the seeded admin), so tests are independent and never mutate the shared seed catalog.</p>
 */
@AutoConfigureMockMvc
class CartOrderIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    /** Spy so we can assert the async, after-commit notification actually ran. */
    @MockitoSpyBean
    private OrderCreatedListener orderCreatedListener;

    // ---------------------------------------------------------------- happy path

    @Test
    void addThenCheckoutCreatesOrderWithSnapshotAndClearsCart() throws Exception {
        String token = userToken("cart-flow@shop.local");
        long productId = createProduct("IT Flow Headphones", "100.00");

        addToCart(token, productId, 2);

        // Cart reflects the line and totals.
        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalAmount").value(200.00))
                .andExpect(jsonPath("$.items.length()").value(1));

        // Checkout -> 201 with snapshot lines + total.
        MvcResult checkout = mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.totalAmount").value(200.00))
                .andExpect(jsonPath("$.items[0].productName").value("IT Flow Headphones"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(100.00))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andReturn();
        long orderId = body(checkout).get("id").asLong();

        // Cart is now empty.
        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        // Order is retrievable and appears in the history.
        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    // ---------------------------------------------------------------- snapshot

    @Test
    void priceSnapshotIsImmutableAfterProductPriceChanges() throws Exception {
        String admin = adminToken();
        String token = userToken("cart-snapshot@shop.local");
        long productId = createProduct("IT Snapshot Item", "50.00");

        addToCart(token, productId, 1);
        MvcResult checkout = mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long orderId = body(checkout).get("id").asLong();

        // Change the product price AFTER the order was placed.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("IT Snapshot Item", "999.99")))
                .andExpect(status().isOk());

        // The historical order keeps the snapshot price, not the new one.
        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(50.00))
                .andExpect(jsonPath("$.totalAmount").value(50.00));
    }

    // ---------------------------------------------------------------- edge cases

    @Test
    void checkoutWithEmptyCartReturns400() throws Exception {
        String token = userToken("cart-empty@shop.local");
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void deletingProductRemovesItFromCart() throws Exception {
        String admin = adminToken();
        String token = userToken("cart-deleted@shop.local");
        long productId = createProduct("IT Deletable Item", "15.00");

        addToCart(token, productId, 1);

        // Admin deletes the product; cart_items -> products is ON DELETE CASCADE.
        mockMvc.perform(delete("/api/products/{id}", productId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        // The cart line is gone, and checkout now fails as empty.
        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addingSameProductTwiceIncrementsTheLineQuantity() throws Exception {
        String token = userToken("cart-increment@shop.local");
        long productId = createProduct("IT Increment Item", "10.00");

        addToCart(token, productId, 2);
        addToCart(token, productId, 3);

        // One line, quantities summed (2 + 3) by the atomic upsert — not a second row.
        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalAmount").value(50.00));
    }

    @Test
    void deletingAProductThatBelongsToAnOrderReturns409() throws Exception {
        String admin = adminToken();
        String token = userToken("cart-ordered-delete@shop.local");
        long productId = createProduct("IT Ordered Item", "25.00");

        addToCart(token, productId, 1);
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // order_items.product_id is ON DELETE RESTRICT: the product is now part of an immutable
        // order, so deletion must be refused with a clean 409 (ApiError), never a 500.
        mockMvc.perform(delete("/api/products/{id}", productId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    // ---------------------------------------------------------------- concurrency

    @Test
    void concurrentCheckoutsProduceExactlyOneOrder() throws Exception {
        String token = userToken("cart-concurrent@shop.local");
        long userId = userRepository.findByEmail("cart-concurrent@shop.local").orElseThrow().getId();
        long productId = createProduct("IT Concurrent Item", "20.00");
        addToCart(token, productId, 1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tasks.add(() -> {
                try {
                    orderService.checkout(userId);
                    return "ok";
                } catch (BadRequestException ex) {
                    return "empty";
                }
            });
        }
        List<Future<String>> results = pool.invokeAll(tasks);
        pool.shutdown();

        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get());
        }

        // Exactly one checkout succeeds; the other sees an empty cart (serialized by the row lock).
        assertThat(outcomes).filteredOn("ok"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("empty"::equals).hasSize(1);
        assertThat(orderRepository.findByUserId(userId,
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- roles / ownership

    @Test
    void cartAndOrderEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotReadAnotherUsersOrder() throws Exception {
        String tokenA = userToken("cart-owner-a@shop.local");
        String tokenB = userToken("cart-owner-b@shop.local");
        long productId = createProduct("IT Ownership Item", "30.00");

        addToCart(tokenA, productId, 1);
        MvcResult checkout = mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andReturn();
        long orderId = body(checkout).get("id").asLong();

        // User B must not see user A's order -> 404 (we never reveal existence).
        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- async event

    @Test
    void orderCreatedEventIsHandledAsynchronouslyAfterCommit() throws Exception {
        Mockito.clearInvocations(orderCreatedListener);
        String token = userToken("cart-event@shop.local");
        long productId = createProduct("IT Event Item", "10.00");
        addToCart(token, productId, 1);

        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // The listener runs after commit on a background thread — await its invocation.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                Mockito.verify(orderCreatedListener).onOrderCreated(Mockito.any(OrderCreatedEvent.class)));
    }

    // ---------------------------------------------------------------- helpers

    private void addToCart(String token, long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private long createProduct(String name, String price) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(name, price)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asLong();
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
