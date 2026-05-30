package com.shop.product.dto;

import java.math.BigDecimal;

/**
 * Catalog filter criteria carrier. Built in the controller from individually-validated query
 * params (see {@code ProductController}) and consumed by {@code ProductSpecifications.build}.
 *
 * <p>Each field is optional: a {@code null}/blank field is simply omitted from the resulting
 * {@code Specification}. A {@code minPrice} greater than {@code maxPrice} is NOT an error — it
 * yields an empty page (the two predicates are simply unsatisfiable together).</p>
 *
 * @param name       partial, case-insensitive match on product name (ILIKE)
 * @param categoryId exact category match
 * @param minPrice   inclusive lower price bound
 * @param maxPrice   inclusive upper price bound
 */
public record ProductFilter(
        String name,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
