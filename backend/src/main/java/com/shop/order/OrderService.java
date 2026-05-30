package com.shop.order;

import com.shop.cart.domain.Cart;
import com.shop.cart.domain.CartItem;
import com.shop.cart.repository.CartItemRepository;
import com.shop.cart.repository.CartRepository;
import com.shop.common.PageResponse;
import com.shop.error.BadRequestException;
import com.shop.error.ResourceNotFoundException;
import com.shop.order.domain.Order;
import com.shop.order.domain.OrderItem;
import com.shop.order.domain.OrderStatus;
import com.shop.order.dto.OrderResponse;
import com.shop.order.dto.OrderSummaryResponse;
import com.shop.order.event.OrderCreatedEvent;
import com.shop.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order use-cases (service layer). Owns the checkout transaction boundary and order reads.
 *
 * <p><b>Checkout transactionality:</b> reading the cart, creating the {@code order} + {@code
 * order_items}, and clearing the cart all happen in one transaction — all-or-nothing, so the cart
 * is never cleared without an order and vice versa. The cart row is taken under a pessimistic
 * write lock to serialize concurrent checkouts of the same cart (the second one then sees an empty
 * cart → 400).</p>
 *
 * <p><b>Price snapshot (risk management):</b> each line copies the product's {@code name} and
 * {@code price} <i>at checkout time</i> into the {@code order_item}. The order total is the sum of
 * these snapshots. Later price/name edits or product deletion therefore cannot mutate a historical
 * order — the order is an immutable financial document, decoupled from the mutable catalog.</p>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository,
                        CartRepository cartRepository,
                        CartItemRepository cartItemRepository,
                        OrderMapper orderMapper,
                        ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Places an order from the user's current cart in a single transaction: snapshots prices,
     * persists the order, clears the cart, and publishes an {@link OrderCreatedEvent}.
     *
     * @throws BadRequestException if the cart is missing or empty
     */
    @Transactional
    public OrderResponse checkout(Long userId) {
        log.debug("Checkout starting for user id={}", userId);

        // Pessimistic lock on the cart row serializes concurrent checkouts of the same cart.
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BadRequestException("Cart is empty"));
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        Order order = new Order();
        order.setUser(cart.getUser());
        order.setStatus(OrderStatus.NEW);

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem cartItem : items) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            // Snapshot: copy name + price as they are right now.
            orderItem.setProductName(cartItem.getProduct().getName());
            orderItem.setUnitPrice(cartItem.getProduct().getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            order.getItems().add(orderItem);
            total = total.add(orderItem.getUnitPrice().multiply(BigDecimal.valueOf(orderItem.getQuantity())));
        }
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);          // cascades to order_items
        cartItemRepository.deleteAll(items);                // clears the cart (same transaction)

        log.info("Order created id={} user id={} total={} items={}",
                saved.getId(), userId, total, items.size());

        // Published now, but handled AFTER_COMMIT + @Async (see OrderCreatedListener).
        eventPublisher.publishEvent(new OrderCreatedEvent(
                saved.getId(), userId, saved.getTotalAmount(), saved.getCreatedAt()));

        return orderMapper.toResponse(saved);
    }

    /** Returns one order owned by the user (with items). Another user's order resolves to 404. */
    @Transactional(readOnly = true)
    public OrderResponse get(Long userId, Long orderId) {
        log.debug("Get order id={} for user id={}", orderId, userId);
        return orderMapper.toResponse(orderRepository.findWithItemsByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId)));
    }

    /** Returns the user's paginated order history (summaries, newest first by default). */
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listMyOrders(Long userId, Pageable pageable) {
        log.debug("List orders for user id={} page={} size={}",
                userId, pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(orderRepository.findByUserId(userId, pageable), orderMapper::toSummary);
    }
}
