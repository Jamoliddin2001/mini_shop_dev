package com.shop.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles {@link OrderCreatedEvent} out of band (design pattern: <b>Observer</b>).
 *
 * <p><b>Why {@code @TransactionalEventListener(AFTER_COMMIT)}:</b> the notification fires only
 * once the checkout transaction has actually committed — we never tell a customer "order placed"
 * for an order that was rolled back.</p>
 *
 * <p><b>Why {@code @Async}:</b> the side effect runs on a bounded background pool (see
 * {@code AsyncConfig}), so a slow/failing notification never blocks or fails the HTTP request that
 * already succeeded.</p>
 *
 * <p>This is a stub that logs a "notification sent" line. In production this is where the event
 * would be published to a message broker (Kafka/RabbitMQ/SQS) for a dedicated notification
 * service — see README → "Дальнейшее масштабирование". Exceptions are swallowed (logged) on
 * purpose: a failed notification must not affect the already-committed order.</p>
 */
@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    @Async("orderEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            // Stub for a real notification (email/push). No PII is logged — ids and amount only.
            log.info("[notification] Order #{} placed by user #{} for total {} at {} — notification stub "
                            + "(in prod this would be published to a message broker)",
                    event.orderId(), event.userId(), event.totalAmount(), event.createdAt());
        } catch (RuntimeException ex) {
            // Best-effort side effect: never propagate — the order is already committed.
            log.error("[notification] Failed to process OrderCreatedEvent for order #{}", event.orderId(), ex);
        }
    }
}
