package com.shop.order.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link OrderCreatedListener}: the notification stub processes an event without
 * throwing — it is a best-effort side effect that must never disturb the already-committed order.
 * The async/after-commit wiring is covered end-to-end in {@code CartOrderIT}; here we cover the
 * handler body in isolation (no Spring context).
 *
 * <p>Note: the {@code catch (RuntimeException)} branch is defensive and not force-covered — with a
 * non-null event none of the record accessors throw, so the branch is effectively unreachable
 * without mocking the static logger (which would be over-engineering for a logging stub).</p>
 */
class OrderCreatedListenerTest {

    private final OrderCreatedListener listener = new OrderCreatedListener();

    @Test
    void processesAValidEventWithoutThrowing() {
        OrderCreatedEvent event =
                new OrderCreatedEvent(99L, 7L, new BigDecimal("155.79"), Instant.now());

        assertThatCode(() -> listener.onOrderCreated(event)).doesNotThrowAnyException();
    }

    @Test
    void toleratesNullFieldsWithoutThrowing() {
        // Null fields render as "null" in the log line — the stub must still not blow up.
        OrderCreatedEvent event = new OrderCreatedEvent(1L, 2L, null, null);

        assertThatCode(() -> listener.onOrderCreated(event)).doesNotThrowAnyException();
    }
}
