package com.shop.product.graphql;

import com.shop.common.PageResponse;
import com.shop.error.ResourceNotFoundException;
import com.shop.product.ProductService;
import com.shop.product.dto.ProductCreateRequest;
import com.shop.product.dto.ProductFilter;
import com.shop.product.dto.ProductResponse;
import com.shop.product.dto.ProductUpdateRequest;
import com.shop.product.graphql.input.CreateProductInput;
import com.shop.product.graphql.input.ProductFilterInput;
import com.shop.product.graphql.input.UpdateProductInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductGraphQLController}: resolver methods delegate correctly to
 * {@link ProductService}. No Spring context — pure Mockito, fast execution.
 */
@ExtendWith(MockitoExtension.class)
class ProductGraphQLControllerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductGraphQLController controller;

    private static ProductResponse response(Long id) {
        return new ProductResponse(id, "Laptop", "desc", new BigDecimal("999.99"),
                "https://img.example.com/1.jpg", null, null, Instant.now());
    }

    // ─── Query: product ───────────────────────────────────────────────────────

    @Test
    void product_returnsMappedDto_whenProductExists() {
        when(productService.get(1L)).thenReturn(response(1L));

        ProductResponse result = controller.product(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Laptop");
    }

    @Test
    void product_propagatesNotFoundException_whenProductMissing() {
        when(productService.get(404L)).thenThrow(new ResourceNotFoundException("Product", 404L));

        assertThatThrownBy(() -> controller.product(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product 404");
    }

    // ─── Query: products ──────────────────────────────────────────────────────

    @Test
    void products_buildsCorrectPageRequest_andReturnsPage() {
        PageResponse<ProductResponse> page = new PageResponse<>(List.of(response(1L)), 0, 10, 1L, 1);
        when(productService.list(any(ProductFilter.class), any(Pageable.class))).thenReturn(page);

        PageResponse<ProductResponse> result = controller.products(null, 0, 10);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).list(any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void products_withNullFilter_usesEmptyFilter() {
        when(productService.list(any(), any())).thenReturn(
                new PageResponse<>(List.of(), 0, 20, 0L, 0));

        controller.products(null, 0, 20);

        ArgumentCaptor<ProductFilter> filterCaptor = ArgumentCaptor.forClass(ProductFilter.class);
        verify(productService).list(filterCaptor.capture(), any());
        ProductFilter captured = filterCaptor.getValue();
        assertThat(captured.name()).isNull();
        assertThat(captured.categoryId()).isNull();
        assertThat(captured.minPrice()).isNull();
        assertThat(captured.maxPrice()).isNull();
    }

    @Test
    void products_withFilter_mapsAllFields() {
        when(productService.list(any(), any())).thenReturn(
                new PageResponse<>(List.of(), 0, 20, 0L, 0));
        ProductFilterInput filter = new ProductFilterInput("phone", 2L,
                new BigDecimal("100"), new BigDecimal("500"));

        controller.products(filter, 0, 20);

        ArgumentCaptor<ProductFilter> captor = ArgumentCaptor.forClass(ProductFilter.class);
        verify(productService).list(captor.capture(), any());
        assertThat(captor.getValue().name()).isEqualTo("phone");
        assertThat(captor.getValue().categoryId()).isEqualTo(2L);
        assertThat(captor.getValue().minPrice()).isEqualByComparingTo("100");
        assertThat(captor.getValue().maxPrice()).isEqualByComparingTo("500");
    }

    // ─── Mutation: createProduct ──────────────────────────────────────────────

    @Test
    void createProduct_delegatesToService_withCorrectBigDecimal() {
        CreateProductInput input = new CreateProductInput("Phone", "desc", 299.99, null, null);
        when(productService.create(any(ProductCreateRequest.class))).thenReturn(response(5L));

        ProductResponse result = controller.createProduct(input);

        assertThat(result.id()).isEqualTo(5L);
        ArgumentCaptor<ProductCreateRequest> captor = ArgumentCaptor.forClass(ProductCreateRequest.class);
        verify(productService).create(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Phone");
        // BigDecimal.valueOf(299.99) avoids floating-point representation issues
        assertThat(captor.getValue().price()).isEqualByComparingTo("299.99");
    }

    // ─── Mutation: updateProduct ──────────────────────────────────────────────

    @Test
    void updateProduct_passesIdAndMappedRequest_toService() {
        UpdateProductInput input = new UpdateProductInput("Updated", null, 399.0, null, 3L);
        when(productService.update(eq(7L), any(ProductUpdateRequest.class))).thenReturn(response(7L));

        controller.updateProduct(7L, input);

        ArgumentCaptor<ProductUpdateRequest> captor = ArgumentCaptor.forClass(ProductUpdateRequest.class);
        verify(productService).update(eq(7L), captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Updated");
        assertThat(captor.getValue().categoryId()).isEqualTo(3L);
    }

    // ─── Mutation: deleteProduct ──────────────────────────────────────────────

    @Test
    void deleteProduct_callsServiceAndReturnsTrue() {
        boolean result = controller.deleteProduct(8L);

        verify(productService).delete(8L);
        assertThat(result).isTrue();
    }

    @Test
    void deleteProduct_propagatesNotFoundException_whenProductMissing() {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Product", 99L))
                .when(productService).delete(99L);

        assertThatThrownBy(() -> controller.deleteProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
