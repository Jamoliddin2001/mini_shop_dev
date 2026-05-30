package com.shop.order.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when an order is successfully created (design pattern:
 * <b>Domain Event</b> / Observer). Carries only non-PII identifiers and amounts — never the
 * customer's email or any cart contents.
 *
 * <p>Published via {@code ApplicationEventPublisher} inside the checkout transaction; handled by
 * {@link OrderCreatedListener} <i>after commit</i> and asynchronously, so the side effect
 * (notification) never runs for a rolled-back order and never blocks the HTTP thread.</p>
 *
 * @param orderId     created order id
 * @param userId      owner user id (identifier only, not PII)
 * @param totalAmount order total
 * @param createdAt   creation timestamp
 */
public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        BigDecimal totalAmount,
        Instant createdAt
) {
}
