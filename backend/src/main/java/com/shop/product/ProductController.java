package com.shop.product;

import com.shop.common.PageResponse;
import com.shop.error.BadRequestException;
import com.shop.product.dto.ProductCreateRequest;
import com.shop.product.dto.ProductFilter;
import com.shop.product.dto.ProductResponse;
import com.shop.product.dto.ProductUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Set;

/**
 * Catalog endpoints (controller layer — thin; logic in {@link ProductService}).
 *
 * <p>Reads are public ({@code GET}); writes are admin-only — enforced both at the route level
 * ({@code SecurityConfig}) and here via method-level {@link PreAuthorize} (defense-in-depth).
 * {@code @Validated} activates the bean-validation constraints on the filter query params.</p>
 */
@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    /** Sortable fields are whitelisted: prevents sorting by arbitrary/non-indexed columns. */
    private static final Set<String> SORTABLE = Set.of("name", "price", "createdAt");

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** Public, paginated, filtered catalog listing. */
    @GetMapping
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal minPrice,
            @RequestParam(required = false) @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal maxPrice,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        validateSort(pageable.getSort());
        validatePriceRange(minPrice, maxPrice);
        log.info("GET /api/products name={} categoryId={} minPrice={} maxPrice={} page={} size={}",
                name, categoryId, minPrice, maxPrice, pageable.getPageNumber(), pageable.getPageSize());
        ProductFilter filter = new ProductFilter(name, categoryId, minPrice, maxPrice);
        return productService.list(filter, pageable);
    }

    /** Public single-product lookup. */
    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        log.info("GET /api/products/{}", id);
        return productService.get(id);
    }

    /** Admin-only product creation. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        log.info("POST /api/products");
        ProductResponse created = productService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Admin-only full update. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ProductUpdateRequest request) {
        log.info("PUT /api/products/{}", id);
        return productService.update(id, request);
    }

    /** Admin-only delete. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("DELETE /api/products/{}", id);
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Rejects sort requests on non-whitelisted properties with a 400 (ApiError) before querying. */
    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BadRequestException(
                        "Unsupported sort property: '" + order.getProperty() + "'. Allowed: " + SORTABLE);
            }
        }
    }

    /**
     * Rejects an inverted price range with a 400 (ApiError) before querying. Each bound is already
     * validated individually (non-negative, ≤2 decimals); this is the one cross-field rule the
     * per-parameter constraints cannot express. Either bound being absent is a valid open range.
     */
    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice must be <= maxPrice");
        }
    }
}
