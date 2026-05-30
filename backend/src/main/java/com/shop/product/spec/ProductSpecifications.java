package com.shop.product.spec;

import com.shop.product.domain.Product;
import com.shop.product.dto.ProductFilter;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic catalog filter built with the JPA <b>Specification</b> pattern. Each criterion is an
 * independent <b>Strategy</b> ({@code Specification<Product>}); an absent criterion contributes
 * {@code null} and is dropped from the AND-composition. This is preferred over a cascade of
 * {@code if}-statements / N repository methods: criteria are isolated, testable, and adding a new
 * filter is one new method plus one line in {@link #build} (Open/Closed).
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /** Case-insensitive partial match on name; backed by the {@code idx_products_name_trgm} GIN index. */
    public static Specification<Product> nameContains(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String pattern = "%" + name.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }

    /** Exact category match. */
    public static Specification<Product> hasCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    /** Inclusive lower price bound. */
    public static Specification<Product> priceGte(BigDecimal minPrice) {
        if (minPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    /** Inclusive upper price bound. */
    public static Specification<Product> priceLte(BigDecimal maxPrice) {
        if (maxPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    /**
     * Eagerly fetches the LAZY {@code category} to prevent N+1 when mapping a page of products.
     *
     * <p>The fetch is applied ONLY to the data query, not to the pagination {@code count} query
     * ({@code resultType == Long.class}) — adding a fetch/join to a count query is invalid and
     * throws. This is the well-known {@code JpaSpecificationExecutor} + {@code Pageable} pitfall.</p>
     *
     * <p>Safe with pagination because {@code category} is a <b>to-one</b> association
     * ({@code @ManyToOne}); {@code LEFT JOIN FETCH} + {@code Pageable} paginates in SQL with no
     * {@code HHH000104} "applying in memory" warning (that affects only to-many collection fetches).</p>
     */
    public static Specification<Product> fetchCategory() {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("category", JoinType.LEFT);
            }
            return null; // adds the fetch as a side effect; contributes no predicate
        };
    }

    /**
     * Composes the active criteria with AND and attaches the category fetch-join. An empty filter
     * yields a specification that matches everything (still with the fetch-join applied).
     */
    public static Specification<Product> build(ProductFilter filter) {
        List<Specification<Product>> criteria = new ArrayList<>();
        criteria.add(fetchCategory());
        criteria.add(nameContains(filter.name()));
        criteria.add(hasCategory(filter.categoryId()));
        criteria.add(priceGte(filter.minPrice()));
        criteria.add(priceLte(filter.maxPrice()));
        // allOf ignores null entries, so absent criteria simply drop out of the AND.
        return Specification.allOf(criteria);
    }
}
