package com.shop.product;

import com.shop.common.PageResponse;
import com.shop.error.ResourceNotFoundException;
import com.shop.product.domain.Category;
import com.shop.product.domain.Product;
import com.shop.product.dto.ProductCreateRequest;
import com.shop.product.dto.ProductFilter;
import com.shop.product.dto.ProductResponse;
import com.shop.product.dto.ProductUpdateRequest;
import com.shop.product.repository.CategoryRepository;
import com.shop.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
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
 * Unit tests for {@link ProductService}: CRUD happy paths, 404 on missing product/category, and
 * that listing delegates to the repository (Specification + Pageable) and wraps the result in a
 * {@link PageResponse}. Collaborators are mocked — no Spring context, no real DB.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    private static ProductResponse sampleResponse(Long id) {
        return new ProductResponse(id, "Phone", "desc", new BigDecimal("9.99"),
                "https://example.com/p.jpg", null, null, Instant.now());
    }

    private static ProductCreateRequest createRequest(Long categoryId) {
        return new ProductCreateRequest("Phone", "desc", new BigDecimal("9.99"),
                "https://example.com/p.jpg", categoryId);
    }

    @Test
    void createWithoutCategoryPersistsProductAndDoesNotTouchCategoryRepo() {
        Product entity = new Product();
        when(productMapper.toEntity(any())).thenReturn(entity);
        when(productRepository.save(entity)).thenAnswer(inv -> {
            entity.setId(10L);
            return entity;
        });
        when(productMapper.toResponse(entity)).thenReturn(sampleResponse(10L));

        ProductResponse result = productService.create(createRequest(null));

        assertThat(result.id()).isEqualTo(10L);
        assertThat(entity.getCategory()).isNull();
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void createWithCategoryResolvesAndAttachesIt() {
        Product entity = new Product();
        Category category = new Category();
        category.setId(3L);
        category.setName("Electronics");
        when(productMapper.toEntity(any())).thenReturn(entity);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(productRepository.save(entity)).thenReturn(entity);
        when(productMapper.toResponse(entity)).thenReturn(sampleResponse(11L));

        productService.create(createRequest(3L));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isSameAs(category);
    }

    @Test
    void createWithMissingCategoryThrows404AndDoesNotSave() {
        when(productMapper.toEntity(any())).thenReturn(new Product());
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(createRequest(99L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category 99");

        verify(productRepository, never()).save(any());
    }

    @Test
    void getReturnsMappedProduct() {
        Product entity = new Product();
        entity.setId(5L);
        when(productRepository.findById(5L)).thenReturn(Optional.of(entity));
        when(productMapper.toResponse(entity)).thenReturn(sampleResponse(5L));

        assertThat(productService.get(5L).id()).isEqualTo(5L);
    }

    @Test
    void getMissingProductThrows404() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.get(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product 404");
    }

    @Test
    void updateAppliesChangesToExistingProduct() {
        Product entity = new Product();
        entity.setId(7L);
        when(productRepository.findById(7L)).thenReturn(Optional.of(entity));
        when(productRepository.save(entity)).thenReturn(entity);
        when(productMapper.toResponse(entity)).thenReturn(sampleResponse(7L));

        ProductUpdateRequest request = new ProductUpdateRequest("New", "d", new BigDecimal("1.00"), null, null);
        ProductResponse result = productService.update(7L, request);

        assertThat(result.id()).isEqualTo(7L);
        verify(productMapper).updateEntity(entity, request);
        verify(productRepository).save(entity);
    }

    @Test
    void updateMissingProductThrows404() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        ProductUpdateRequest request = new ProductUpdateRequest("New", "d", new BigDecimal("1.00"), null, null);
        assertThatThrownBy(() -> productService.update(404L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteRemovesExistingProduct() {
        when(productRepository.existsById(8L)).thenReturn(true);

        productService.delete(8L);

        verify(productRepository).deleteById(8L);
    }

    @Test
    void deleteMissingProductThrows404AndDoesNotDelete() {
        when(productRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> productService.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, never()).deleteById(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listDelegatesToRepositoryAndWrapsInPageResponse() {
        Product entity = new Product();
        entity.setId(1L);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(productMapper.toResponse(entity)).thenReturn(sampleResponse(1L));

        PageResponse<ProductResponse> result =
                productService.list(new ProductFilter(null, null, null, null), pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(1L);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
    }
}
