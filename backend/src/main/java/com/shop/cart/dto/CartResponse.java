package com.shop.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The current user's cart as returned at the API boundary. The {@code Cart} entity is never
 * exposed directly; mapping is done by {@code CartMapper}.
 *
 * @param items       cart lines (may be empty for a fresh/cleared cart)
 * @param totalAmount sum of all {@code lineTotal} values (priced at current product prices)
 * @param totalItems  total number of units across all lines (sum of quantities)
 */
public record CartResponse(
        List<CartItemResponse> items,
        BigDecimal totalAmount,
        int totalItems
) {
}
