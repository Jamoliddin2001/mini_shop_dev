package com.shop.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Payload to add a product to the current user's cart. Validated at the controller boundary
 * ({@code @Valid}).
 *
 * <p>If the product is already in the cart, the service <b>adds</b> {@code quantity} to the
 * existing line (it does not replace it) — classic "add more" semantics that keeps the
 * {@code UNIQUE(cart_id, product_id)} invariant (one row per product).</p>
 *
 * @param productId existing product id (required, positive); a missing product yields 404
 * @param quantity  units to add (required, ≥ 1)
 */
public record AddToCartRequest(
        @NotNull @Positive Long productId,
        @NotNull @Min(1) Integer quantity
) {
}
