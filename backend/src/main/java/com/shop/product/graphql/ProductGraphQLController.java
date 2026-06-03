package com.shop.product.graphql;

import com.shop.common.PageResponse;
import com.shop.product.ProductService;
import com.shop.product.dto.ProductCreateRequest;
import com.shop.product.dto.ProductFilter;
import com.shop.product.dto.ProductResponse;
import com.shop.product.dto.ProductUpdateRequest;
import com.shop.product.graphql.input.CreateProductInput;
import com.shop.product.graphql.input.ProductFilterInput;
import com.shop.product.graphql.input.UpdateProductInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;

/**
 * GraphQL resolvers for the product catalog (design pattern: <b>Controller</b> in the
 * Spring for GraphQL sense — {@code @Controller} beans are scanned by Spring GraphQL's
 * annotation-driven wiring, unlike {@code @RestController} which is HTTP-only).
 *
 * <p>All resolvers delegate to the same {@link ProductService} used by the REST layer — no
 * business logic lives here. The REST controllers ({@code ProductController}) are not modified.</p>
 *
 * <p>Security: query resolvers are public (product reads are unauthenticated, matching REST).
 * Mutation resolvers are admin-only via {@code @PreAuthorize} — method security is already
 * enabled in {@code SecurityConfig} via {@code @EnableMethodSecurity}.</p>
 */
@Controller
public class ProductGraphQLController {

    private static final Logger log = LoggerFactory.getLogger(ProductGraphQLController.class);

    private final ProductService productService;

    public ProductGraphQLController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Resolves {@code query { product(id: ID!) }}.
     * Returns {@code null} (GraphQL null) when the product is not found — the exception handler
     * converts {@code ResourceNotFoundException} to a GraphQL NOT_FOUND error instead.
     */
    @QueryMapping
    public ProductResponse product(@Argument Long id) {
        log.info("GraphQL query product id={}", id);
        return productService.get(id);
    }

    /**
     * Resolves {@code query { products(filter, page, size) }}.
     * {@code page} and {@code size} default to 0/20 in the schema; the service caps size via
     * Spring's {@code max-page-size} configuration.
     */
    @QueryMapping
    public PageResponse<ProductResponse> products(
            @Argument ProductFilterInput filter,
            @Argument int page,
            @Argument int size) {
        log.info("GraphQL query products filter={} page={} size={}", filter, page, size);
        ProductFilter domainFilter = toFilter(filter);
        return productService.list(domainFilter, PageRequest.of(page, size));
    }

    private ProductFilter toFilter(ProductFilterInput input) {
        if (input == null) {
            return new ProductFilter(null, null, null, null);
        }
        return new ProductFilter(input.name(), input.categoryId(), input.minPrice(), input.maxPrice());
    }

    // ─── Mutations ────────────────────────────────────────────────────────────

    /** Resolves {@code mutation { createProduct(input) }}. Admin-only. */
    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse createProduct(@Argument CreateProductInput input) {
        log.info("GraphQL mutation createProduct name={}", input.name());
        ProductCreateRequest request = new ProductCreateRequest(
                input.name(),
                input.description(),
                BigDecimal.valueOf(input.price()),
                input.imageUrl(),
                input.categoryId());
        return productService.create(request);
    }

    /** Resolves {@code mutation { updateProduct(id, input) }}. Admin-only. */
    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse updateProduct(@Argument Long id, @Argument UpdateProductInput input) {
        log.info("GraphQL mutation updateProduct id={}", id);
        ProductUpdateRequest request = new ProductUpdateRequest(
                input.name(),
                input.description(),
                BigDecimal.valueOf(input.price()),
                input.imageUrl(),
                input.categoryId());
        return productService.update(id, request);
    }

    /** Resolves {@code mutation { deleteProduct(id) }}. Admin-only. */
    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public boolean deleteProduct(@Argument Long id) {
        log.info("GraphQL mutation deleteProduct id={}", id);
        productService.delete(id);
        return true;
    }
}
