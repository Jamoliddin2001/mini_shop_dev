package com.shop.cart.dto;

import java.math.BigDecimal;

/**
 * A single cart line returned at the API boundary. The {@code CartItem} entity is never exposed
 * directly; mapping is done by {@code CartMapper}.
 *
 * <p>{@code unitPrice} reflects the product's <b>current</b> price — the cart is a live offer, not
 * a committed document, so it always shows today's price. The price is snapshotted only at
 * checkout (see {@code OrderItem}). {@code lineTotal} is {@code unitPrice × quantity}.</p>
 *
 * @param productId   product id
 * @param productName product name (current)
 * @param unitPrice   current product price (NUMERIC(12,2))
 * @param quantity    units of this product in the cart
 * @param lineTotal   {@code unitPrice × quantity}
 */
public record CartItemResponse(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
}
