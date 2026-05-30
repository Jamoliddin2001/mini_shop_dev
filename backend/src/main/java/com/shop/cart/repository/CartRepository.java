package com.shop.cart.repository;

import com.shop.cart.domain.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Persistence access for {@link Cart} (one per user).
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);

    /**
     * Loads the cart together with its items and their products in a single query (avoids N+1 on
     * the LAZY {@code items}/{@code product} associations when rendering the cart).
     */
    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select c from Cart c where c.user.id = :userId")
    Optional<Cart> findWithItemsByUserId(@Param("userId") Long userId);

    /**
     * Loads the cart row under a {@code PESSIMISTIC_WRITE} lock ({@code SELECT … FOR UPDATE}).
     * Used at checkout to serialize concurrent checkouts of the same cart: a second concurrent
     * checkout blocks until the first commits, then sees an empty cart. Only the {@code cart} row
     * is locked — items are loaded separately within the same transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.user.id = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);
}
