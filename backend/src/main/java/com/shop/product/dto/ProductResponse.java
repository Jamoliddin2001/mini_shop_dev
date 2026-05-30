package com.shop.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Product representation returned at the API boundary. The {@code Product} entity is never
 * exposed directly; mapping is done by {@code ProductMapper}.
 *
 * <p>{@code categoryId}/{@code categoryName} are {@code null} when the product has no category
 * (the FK is {@code ON DELETE SET NULL}).</p>
 *
 * @param id           product id
 * @param name         product name
 * @param description  optional long description
 * @param price        price (NUMERIC(12,2))
 * @param imageUrl     optional image URL (images are stored as URLs, not uploaded — see README)
 * @param categoryId   optional category id
 * @param categoryName optional category name (denormalized for client convenience)
 * @param createdAt    creation timestamp
 */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        Long categoryId,
        String categoryName,
        Instant createdAt
) {
}
