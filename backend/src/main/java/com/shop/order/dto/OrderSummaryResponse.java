package com.shop.order.dto;

import com.shop.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Compact order representation for the paginated order-history list. Deliberately omits line
 * items: that keeps the list query free of a to-many fetch join (which would force in-memory
 * pagination and risk N+1). Line details are available via {@code GET /api/orders/{id}}.
 *
 * @param id          order id
 * @param status      lifecycle status
 * @param totalAmount order total (snapshot)
 * @param createdAt   creation timestamp
 */
public record OrderSummaryResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt
) {
}
