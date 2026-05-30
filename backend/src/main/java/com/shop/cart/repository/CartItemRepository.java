package com.shop.cart.repository;

import com.shop.cart.domain.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Persistence access for {@link CartItem}.
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /** Finds a specific product line within a cart (used to increment quantity on re-add / remove). */
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    /** Loads all lines of a cart with their products in one query (avoids N+1 at checkout). */
    @EntityGraph(attributePaths = {"product"})
    List<CartItem> findByCartId(Long cartId);

    /**
     * Atomically inserts a new cart line or increments the quantity of the existing one for the
     * given {@code (cart, product)} — a single {@code INSERT ... ON CONFLICT DO UPDATE} serialized
     * by the {@code uq_cart_items_cart_product} unique constraint. This is the race-free equivalent
     * of a find-then-save/increment: concurrent "add" of the same product can neither lose an
     * increment (lost update) nor fail on the unique constraint.
     */
    @Modifying
    @Query(value = """
            INSERT INTO cart_items (cart_id, product_id, quantity)
            VALUES (:cartId, :productId, :quantity)
            ON CONFLICT (cart_id, product_id)
            DO UPDATE SET quantity = cart_items.quantity + EXCLUDED.quantity
            """, nativeQuery = true)
    void incrementOrInsert(@Param("cartId") Long cartId,
                           @Param("productId") Long productId,
                           @Param("quantity") int quantity);
}
