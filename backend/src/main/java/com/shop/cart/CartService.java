package com.shop.cart;

import com.shop.cart.domain.Cart;
import com.shop.cart.domain.CartItem;
import com.shop.cart.dto.AddToCartRequest;
import com.shop.cart.dto.CartItemResponse;
import com.shop.cart.dto.CartResponse;
import com.shop.cart.repository.CartItemRepository;
import com.shop.cart.repository.CartRepository;
import com.shop.error.ResourceNotFoundException;
import com.shop.product.domain.Product;
import com.shop.product.repository.ProductRepository;
import com.shop.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Server-side cart use-cases (service layer). Controllers stay thin; all business rules and
 * transaction boundaries live here.
 *
 * <p>The cart is created lazily on first write (never on a read). Items are priced at the
 * product's <b>current</b> price — the cart is a live offer, so the price is snapshotted only at
 * checkout ({@code OrderService}). Re-adding an existing product <b>increments</b> its quantity,
 * preserving the {@code UNIQUE(cart_id, product_id)} invariant.</p>
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       UserRepository userRepository,
                       CartMapper cartMapper) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.cartMapper = cartMapper;
    }

    /** Returns the user's cart (with items + products fetched in one query). Empty if none exists yet. */
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        log.debug("Loading cart for user id={}", userId);
        return cartRepository.findWithItemsByUserId(userId)
                .map(cart -> assemble(cart.getItems()))
                .orElseGet(() -> emptyCart());
    }

    /**
     * Adds {@code request.quantity} of a product to the user's cart. Increments the quantity if the
     * product is already present, otherwise creates a new line. Throws 404 if the product does not
     * exist (e.g. was deleted).
     */
    @Transactional
    public CartResponse addItem(Long userId, AddToCartRequest request) {
        log.info("Add to cart: user id={} product id={} qty={}", userId, request.productId(), request.quantity());
        Cart cart = getOrCreateCart(userId);
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));

        // Atomic insert-or-increment in a single statement, serialized by the DB on
        // uq_cart_items_cart_product. This removes the read-modify-write race a separate
        // find + save/increment had: concurrent adds of the same product can neither lose an
        // increment (lost update) nor fail on the unique constraint. Mirrors the care the checkout
        // path takes with its pessimistic lock. Product existence is still checked above so a
        // missing product is a clean 404, not an FK-driven 409.
        cartItemRepository.incrementOrInsert(cart.getId(), product.getId(), request.quantity());
        log.debug("Upserted cart line: cart id={} product id={} (+{})",
                cart.getId(), product.getId(), request.quantity());

        return getCart(userId);
    }

    /** Removes a product line from the user's cart. Throws 404 if the product is not in the cart. */
    @Transactional
    public CartResponse removeItem(Long userId, Long productId) {
        log.info("Remove from cart: user id={} product id={}", userId, productId);
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", productId));
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", productId));
        cartItemRepository.delete(item);
        return getCart(userId);
    }

    /**
     * Returns the user's cart, creating it on first use. Handles the (rare) race where two first
     * writes try to insert a cart concurrently: the loser of the {@code UNIQUE(user_id)} constraint
     * simply re-reads the winner's row.
     */
    Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> createCart(userId));
    }

    private Cart createCart(Long userId) {
        log.debug("Creating cart for user id={}", userId);
        Cart cart = new Cart();
        cart.setUser(userRepository.getReferenceById(userId));
        try {
            return cartRepository.saveAndFlush(cart);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Concurrent cart creation for user id={}, re-reading existing cart", userId);
            return cartRepository.findByUserId(userId)
                    .orElseThrow(() -> ex);
        }
    }

    private CartResponse assemble(List<CartItem> items) {
        List<CartItemResponse> lines = cartMapper.toItemResponses(items);
        BigDecimal total = lines.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalItems = lines.stream().mapToInt(CartItemResponse::quantity).sum();
        log.debug("Cart assembled: {} line(s), {} unit(s), total={}", lines.size(), totalItems, total);
        return new CartResponse(lines, total, totalItems);
    }

    private CartResponse emptyCart() {
        return new CartResponse(List.of(), BigDecimal.ZERO, 0);
    }
}
