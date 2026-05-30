package com.shop.product.dto;

/**
 * Category representation returned at the API boundary (e.g. to populate the catalog filter UI).
 *
 * @param id   category id
 * @param name category name
 */
public record CategoryResponse(
        Long id,
        String name
) {
}
