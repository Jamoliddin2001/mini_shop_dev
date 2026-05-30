package com.shop.order.repository;

import com.shop.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link OrderItem}.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
