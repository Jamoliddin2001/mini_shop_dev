package com.shop.order.repository;

import com.shop.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Persistence access for {@link Order}.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Paginated order history for one user. Returns orders without their items on purpose: the
     * summary list does not fetch the to-many {@code items}, so pagination stays in SQL and there
     * is no N+1. Item details are served by {@link #findWithItemsByIdAndUserId}.
     */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /**
     * Loads a single order owned by {@code userId}, together with its items and their products in
     * one query. Scoping by {@code userId} means another user's order id resolves to empty (the
     * caller maps that to 404 — we never reveal that someone else's order exists).
     */
    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select o from Order o where o.id = :id and o.user.id = :userId")
    Optional<Order> findWithItemsByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
