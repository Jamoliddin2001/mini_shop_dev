package com.shop.order;

import com.shop.cart.domain.Cart;
import com.shop.cart.domain.CartItem;
import com.shop.cart.repository.CartItemRepository;
import com.shop.cart.repository.CartRepository;
import com.shop.error.BadRequestException;
import com.shop.error.ResourceNotFoundException;
import com.shop.order.domain.Order;
import com.shop.order.domain.OrderItem;
import com.shop.order.domain.OrderStatus;
import com.shop.order.dto.OrderResponse;
import com.shop.order.event.OrderCreatedEvent;
import com.shop.order.repository.OrderRepository;
import com.shop.product.domain.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderService}: checkout builds price snapshots from current product
 * prices, clears the cart, and publishes an event; empty/missing cart is rejected with 400; a
 * non-owned order id resolves to 404. Collaborators are mocked — no Spring context, no real DB.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long CART_ID = 10L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    private static Product product(Long id, String name, String price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        return p;
    }

    private static CartItem cartItem(Product product, int qty) {
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(qty);
        return item;
    }

    private static Cart cart() {
        Cart c = new Cart();
        c.setId(CART_ID);
        return c;
    }

    @Test
    void checkoutSnapshotsPricesClearsCartAndPublishesEvent() {
        CartItem itemA = cartItem(product(1L, "Headphones", "129.99"), 1);
        CartItem itemB = cartItem(product(2L, "Mug", "12.90"), 2);
        List<CartItem> items = List.of(itemA, itemB);

        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart()));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(items);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(99L);
            return o;
        });
        when(orderMapper.toResponse(any(Order.class))).thenReturn(
                new OrderResponse(99L, OrderStatus.NEW, new BigDecimal("155.79"), null, List.of()));

        orderService.checkout(USER_ID);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.NEW);
        // total = 129.99*1 + 12.90*2 = 155.79
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("155.79");
        assertThat(saved.getItems()).hasSize(2);
        OrderItem snap = saved.getItems().get(0);
        assertThat(snap.getProductName()).isEqualTo("Headphones");
        assertThat(snap.getUnitPrice()).isEqualByComparingTo("129.99");
        assertThat(snap.getQuantity()).isEqualTo(1);

        // Cart is cleared in the same transaction.
        verify(cartItemRepository).deleteAll(items);

        // Event published with non-PII payload.
        ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(99L);
        assertThat(eventCaptor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(eventCaptor.getValue().totalAmount()).isEqualByComparingTo("155.79");
    }

    @Test
    void checkoutRejectsEmptyCart() {
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart()));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.checkout(USER_ID))
                .isInstanceOf(BadRequestException.class);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutRejectsMissingCart() {
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.checkout(USER_ID))
                .isInstanceOf(BadRequestException.class);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void getThrows404WhenOrderNotOwnedOrMissing() {
        when(orderRepository.findWithItemsByIdAndUserId(123L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.get(USER_ID, 123L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getReturnsMappedOrderWhenOwned() {
        Order order = new Order();
        order.setId(123L);
        order.setStatus(OrderStatus.NEW);
        OrderResponse expected = new OrderResponse(123L, OrderStatus.NEW, new BigDecimal("10.00"), null, List.of());
        when(orderRepository.findWithItemsByIdAndUserId(123L, USER_ID)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(expected);

        assertThat(orderService.get(USER_ID, 123L)).isEqualTo(expected);
    }
}
