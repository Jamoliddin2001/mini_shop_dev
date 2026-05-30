package com.shop.product;

import com.shop.common.PageResponse;
import com.shop.config.CacheConfig;
import com.shop.error.ResourceNotFoundException;
import com.shop.product.domain.Category;
import com.shop.product.domain.Product;
import com.shop.product.dto.ProductCreateRequest;
import com.shop.product.dto.ProductFilter;
import com.shop.product.dto.ProductResponse;
import com.shop.product.dto.ProductUpdateRequest;
import com.shop.product.repository.CategoryRepository;
import com.shop.product.repository.ProductRepository;
import com.shop.product.spec.ProductSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product catalog use-cases (service layer). The controllers stay thin; all business rules and
 * transaction boundaries live here.
 *
 * <p>Reads are filtered dynamically via {@link ProductSpecifications} (Specification pattern) and
 * paginated; the LAZY {@code category} is fetch-joined in the same query to avoid N+1. Writes are
 * admin-only (enforced at the web layer) and validate that a referenced category exists.</p>
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
    }

    /**
     * Returns a filtered, paginated page of products mapped to response DTOs.
     *
     * <p>Cached in {@link CacheConfig#PRODUCT_LIST_CACHE}. The default {@code SimpleKeyGenerator}
     * builds the key from {@code (filter, pageable)} — both are value types with correct
     * equals/hashCode ({@code ProductFilter} is a record, {@code PageRequest} implements them), so
     * distinct queries get distinct entries. We cache the {@link PageResponse} (DTOs), never the
     * JPA entities, so no lazy proxies leak into the cache. The method body below runs only on a
     * cache miss — its DEBUG line therefore traces real DB hits.</p>
     */
    @Cacheable(CacheConfig.PRODUCT_LIST_CACHE)
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(ProductFilter filter, Pageable pageable) {
        log.debug("Cache MISS — querying products filter=[name={}, categoryId={}, minPrice={}, maxPrice={}] page={} size={} sort={}",
                filter.name(), filter.categoryId(), filter.minPrice(), filter.maxPrice(),
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
        Page<Product> page = productRepository.findAll(ProductSpecifications.build(filter), pageable);
        log.debug("Product listing returned {} of {} total", page.getNumberOfElements(), page.getTotalElements());
        return PageResponse.from(page, productMapper::toResponse);
    }

    /** Returns a single product or throws {@link ResourceNotFoundException} (404). */
    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return productMapper.toResponse(product);
    }

    /**
     * Creates a product (admin-only). Resolves the optional category, then persists.
     *
     * <p>Evicts the whole {@link CacheConfig#PRODUCT_LIST_CACHE}: cache keys are composites of
     * (filter, pageable), so we cannot know which paginated/filtered entries a new product would
     * appear in — {@code allEntries = true} is the correct, KISS invalidation.</p>
     */
    @CacheEvict(value = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Product product = productMapper.toEntity(request);
        product.setCategory(resolveCategory(request.categoryId()));
        Product saved = productRepository.save(product);
        log.info("Created product id={} categoryId={}", saved.getId(), request.categoryId());
        return productMapper.toResponse(saved);
    }

    /** Fully updates an existing product (admin-only) or throws 404. Evicts the product-list cache. */
    @CacheEvict(value = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        productMapper.updateEntity(product, request);
        product.setCategory(resolveCategory(request.categoryId()));
        Product saved = productRepository.save(product);
        log.info("Updated product id={} categoryId={}", saved.getId(), request.categoryId());
        return productMapper.toResponse(saved);
    }

    /** Deletes a product (admin-only) or throws 404 if it does not exist. Evicts the product-list cache. */
    @CacheEvict(value = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product", id);
        }
        productRepository.deleteById(id);
        log.info("Deleted product id={}", id);
    }

    /**
     * Loads the referenced category, or returns {@code null} when no category was supplied.
     * A non-null id that does not exist is a client error referencing a missing resource -> 404.
     */
    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }
}
