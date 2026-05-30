package com.shop.product;

import com.shop.config.CacheConfig;
import com.shop.product.domain.Product;
import com.shop.product.dto.ProductCreateRequest;
import com.shop.product.dto.ProductFilter;
import com.shop.product.dto.ProductResponse;
import com.shop.product.repository.CategoryRepository;
import com.shop.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cache-behaviour tests for {@link ProductService}. Unlike {@link ProductServiceTest} (pure Mockito),
 * these need a real Spring context so the {@code @Cacheable}/{@code @CacheEvict} proxies are woven —
 * caching is invisible to a direct method call. The context is intentionally slim: {@link CacheConfig}
 * plus the service and mocked collaborators (no DB, no transaction manager — the inert
 * {@code @Transactional} annotations are ignored without one).
 */
@SpringJUnitConfig(ProductServiceCacheTest.Config.class)
class ProductServiceCacheTest {

    @Configuration
    @Import(CacheConfig.class)
    static class Config {
        @Bean
        ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        CategoryRepository categoryRepository() {
            return mock(CategoryRepository.class);
        }

        @Bean
        ProductMapper productMapper() {
            return mock(ProductMapper.class);
        }

        @Bean
        ProductService productService(ProductRepository pr, CategoryRepository cr, ProductMapper pm) {
            return new ProductService(pr, cr, pm);
        }
    }

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private CacheManager cacheManager;

    private final ProductFilter filter = new ProductFilter(null, null, null, null);
    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void resetState() {
        // Spring caches the context across methods — clear cache + mocks so tests don't bleed into each other.
        reset(productRepository, productMapper);
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.PRODUCT_LIST_CACHE)).clear();
    }

    @SuppressWarnings("unchecked")
    private void stubListQuery() {
        Product entity = new Product();
        entity.setId(1L);
        Page<Product> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(productMapper.toResponse(any(Product.class))).thenReturn(sampleResponse());
    }

    private static ProductResponse sampleResponse() {
        return new ProductResponse(1L, "Phone", "desc", new BigDecimal("9.99"),
                "https://example.com/p.jpg", null, null, Instant.now());
    }

    @SuppressWarnings("unchecked")
    private void verifyDbQueried(int times) {
        verify(productRepository, times(times)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void secondListWithSameFilterIsServedFromCache() {
        stubListQuery();

        productService.list(filter, pageable);
        productService.list(filter, pageable);

        verifyDbQueried(1); // second call hit the cache
    }

    @Test
    void differentFiltersUseDifferentCacheKeys() {
        stubListQuery();

        productService.list(new ProductFilter("phone", null, null, null), pageable);
        productService.list(new ProductFilter("laptop", null, null, null), pageable);

        verifyDbQueried(2); // distinct keys -> two misses
    }

    @Test
    void createEvictsListCache() {
        stubListQuery();
        Product saved = new Product();
        saved.setId(2L);
        when(productMapper.toEntity(any())).thenReturn(saved);
        when(productRepository.save(any())).thenReturn(saved);
        when(productMapper.toResponse(saved)).thenReturn(sampleResponse());

        productService.list(filter, pageable); // miss -> query #1
        productService.create(new ProductCreateRequest(
                "New", "desc", new BigDecimal("1.00"), "https://example.com/n.jpg", null));
        productService.list(filter, pageable); // cache evicted -> query #2

        verifyDbQueried(2);
    }

    @Test
    void deleteEvictsListCache() {
        stubListQuery();
        when(productRepository.existsById(5L)).thenReturn(true);

        productService.list(filter, pageable); // miss -> query #1
        productService.delete(5L);
        productService.list(filter, pageable); // cache evicted -> query #2

        verifyDbQueried(2);
    }
}
