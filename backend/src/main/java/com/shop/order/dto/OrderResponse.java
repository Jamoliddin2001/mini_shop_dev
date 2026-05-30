package com.shop.order.dto;

import com.shop.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full order representation (with line items) returned by checkout and by the single-order
 * lookup. The {@code Order} entity is never exposed directly; mapping is done by
 * {@code OrderMapper}.
 *
 * @param id          order id
 * @param status      lifecycle status (orders are created as {@code NEW})
 * @param totalAmount order total = sum of line totals at purchase time (snapshot)
 * @param createdAt   creation timestamp
 * @param items       order lines (price/name snapshots)
 */
public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        List<OrderItemResponse> items
) {
}
