package com.shop.order;

import com.shop.common.PageResponse;
import com.shop.error.BadRequestException;
import com.shop.order.dto.OrderResponse;
import com.shop.order.dto.OrderSummaryResponse;
import com.shop.security.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;

/**
 * Order endpoints (controller layer — thin; logic in {@link OrderService}).
 *
 * <p>All routes require authentication ({@code SecurityConfig}); a user only ever sees/creates
 * their own orders. The user id is resolved from the JWT principal via {@link CurrentUserService},
 * never from the request. Checkout has no body — the order is built from the server-side cart.</p>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    /** Sortable fields are whitelisted to prevent sorting by arbitrary/non-indexed columns. */
    private static final Set<String> SORTABLE = Set.of("createdAt");

    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    public OrderController(OrderService orderService, CurrentUserService currentUserService) {
        this.orderService = orderService;
        this.currentUserService = currentUserService;
    }

    /** Places an order from the authenticated user's cart. */
    @PostMapping
    public ResponseEntity<OrderResponse> checkout() {
        Long userId = currentUserService.currentUserId();
        log.debug("POST /api/orders user id={}", userId);
        OrderResponse created = orderService.checkout(userId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Paginated history of the authenticated user's orders (newest first by default). */
    @GetMapping
    public PageResponse<OrderSummaryResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        validateSort(pageable.getSort());
        Long userId = currentUserService.currentUserId();
        log.debug("GET /api/orders user id={} page={} size={}",
                userId, pageable.getPageNumber(), pageable.getPageSize());
        return orderService.listMyOrders(userId, pageable);
    }

    /** Single order lookup (own order only; another user's id yields 404). */
    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable Long id) {
        Long userId = currentUserService.currentUserId();
        log.debug("GET /api/orders/{} user id={}", id, userId);
        return orderService.get(userId, id);
    }

    /** Rejects sort requests on non-whitelisted properties with a 400 (ApiError) before querying. */
    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BadRequestException(
                        "Unsupported sort property: '" + order.getProperty() + "'. Allowed: " + SORTABLE);
            }
        }
    }
}
