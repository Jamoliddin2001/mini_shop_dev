package com.shop.cart;

import com.shop.cart.dto.AddToCartRequest;
import com.shop.cart.dto.CartResponse;
import com.shop.security.CurrentUserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cart endpoints (controller layer — thin; logic in {@link CartService}).
 *
 * <p>All routes require authentication ({@code SecurityConfig} → {@code anyRequest().authenticated()});
 * the cart is always the caller's own. The user id is resolved from the JWT principal via
 * {@link CurrentUserService} — never from the request body/path, so one user cannot touch
 * another's cart.</p>
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    private final CartService cartService;
    private final CurrentUserService currentUserService;

    public CartController(CartService cartService, CurrentUserService currentUserService) {
        this.cartService = cartService;
        this.currentUserService = currentUserService;
    }

    /** Returns the authenticated user's cart. */
    @GetMapping
    public CartResponse getCart() {
        Long userId = currentUserService.currentUserId();
        log.debug("GET /api/cart user id={}", userId);
        return cartService.getCart(userId);
    }

    /** Adds a product to the cart (increments quantity if already present). */
    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddToCartRequest request) {
        Long userId = currentUserService.currentUserId();
        log.debug("POST /api/cart/items user id={}", userId);
        return cartService.addItem(userId, request);
    }

    /** Removes a product line from the cart. */
    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable Long productId) {
        Long userId = currentUserService.currentUserId();
        log.debug("DELETE /api/cart/items/{} user id={}", productId, userId);
        return cartService.removeItem(userId, productId);
    }
}
