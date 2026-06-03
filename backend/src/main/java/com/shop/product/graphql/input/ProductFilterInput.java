package com.shop.product.graphql.input;

import java.math.BigDecimal;

/**
 * GraphQL input type for filtering products — mirrors {@code ProductFilterInput} in schema.graphqls.
 * Kept separate from the REST {@code ProductFilter} record to avoid cross-layer coupling.
 */
public record ProductFilterInput(
        String name,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
