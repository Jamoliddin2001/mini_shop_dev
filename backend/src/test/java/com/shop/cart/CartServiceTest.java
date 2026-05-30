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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CartService}: add (new line vs. increment existing), remove, 404 on a
 * missing product / missing cart line, and total recomputation. Collaborators are mocked — no
 * Spring context, no real DB.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long CART_ID = 10L;
    private static final Long PRODUCT_ID = 5L;

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CartMapper cartMapper;

    @InjectMocks
    private CartService cartService;

    private static Product product(Long id, String name, String price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        return p;
    }

    private static Cart cart() {
        Cart c = new Cart();
        c.setId(CART_ID);
        return c;
    }

    /** Stubs the trailing getCart() call that addItem/removeItem make to build the response. */
    private void stubGetCartReturnsEmpty() {
        lenient().when(cartRepository.findWithItemsByUserId(USER_ID)).thenReturn(Optional.of(cart()));
        lenient().when(cartMapper.toItemResponses(anyList())).thenReturn(List.of());
    }

    @Test
    void addItemDelegatesToAtomicUpsertWithRequestedQuantity() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart()));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(PRODUCT_ID, "Mug", "12.90")));
        stubGetCartReturnsEmpty();

        cartService.addItem(USER_ID, new AddToCartRequest(PRODUCT_ID, 3));

        // Insert-vs-increment is decided atomically by the DB (INSERT ... ON CONFLICT), so the
        // service just delegates the requested delta — no read-modify-write in the app layer.
        verify(cartItemRepository).incrementOrInsert(CART_ID, PRODUCT_ID, 3);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItemThrows404WhenProductDoesNotExist() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart()));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new AddToCartRequest(PRODUCT_ID, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(cartItemRepository, never()).incrementOrInsert(any(), any(), anyInt());
    }

    @Test
    void removeItemDeletesTheLine() {
        CartItem existing = new CartItem();
        existing.setId(99L);
        existing.setProduct(product(PRODUCT_ID, "Mug", "12.90"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart()));
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).thenReturn(Optional.of(existing));
        stubGetCartReturnsEmpty();

        cartService.removeItem(USER_ID, PRODUCT_ID);

        verify(cartItemRepository).delete(existing);
    }

    @Test
    void removeItemThrows404WhenProductNotInCart() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart()));
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(USER_ID, PRODUCT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(cartItemRepository, never()).delete(any());
    }

    @Test
    void getCartReturnsEmptyWhenNoCartExists() {
        when(cartRepository.findWithItemsByUserId(USER_ID)).thenReturn(Optional.empty());

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalAmount()).isEqualByComparingTo("0");
        assertThat(response.totalItems()).isZero();
    }

    @Test
    void getCartRecomputesTotalsFromLines() {
        when(cartRepository.findWithItemsByUserId(USER_ID)).thenReturn(Optional.of(cart()));
        when(cartMapper.toItemResponses(anyList())).thenReturn(List.of(
                new CartItemResponse(1L, "A", new BigDecimal("10.00"), 2, new BigDecimal("20.00")),
                new CartItemResponse(2L, "B", new BigDecimal("5.50"), 3, new BigDecimal("16.50"))));

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.totalAmount()).isEqualByComparingTo("36.50");
        assertThat(response.totalItems()).isEqualTo(5);
    }
}
