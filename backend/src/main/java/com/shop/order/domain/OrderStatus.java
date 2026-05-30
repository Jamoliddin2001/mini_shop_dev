package com.shop.order.domain;

/**
 * Lifecycle status of an order. Stored as VARCHAR (see {@code chk_orders_status}).
 */
public enum OrderStatus {
    NEW,
    PAID,
    CANCELLED
}
