package com.shop.order.dto;

import java.math.BigDecimal;

/**
 * A single order line returned at the API boundary. The {@code OrderItem} entity is never exposed
 * directly; mapping is done by {@code OrderMapper}.
 *
 * <p>{@code productName} and {@code unitPrice} are <b>snapshots</b> captured at checkout time, so
 * later edits/deletes of the catalog never mutate a historical order. {@code productId} is kept
 * for reference/linking only. {@code lineTotal} is {@code unitPrice × quantity}.</p>
 *
 * @param productId   referenced product id (the product may have since changed)
 * @param productName product name at purchase time (snapshot)
 * @param unitPrice   product price at purchase time (snapshot, NUMERIC(12,2))
 * @param quantity    units ordered
 * @param lineTotal   {@code unitPrice × quantity}
 */
public record OrderItemResponse(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
}
