package com.shop.product.graphql.input;

/**
 * GraphQL input type for {@code mutation { createProduct(input: CreateProductInput!) }}.
 * Kept separate from {@code ProductCreateRequest} (REST DTO) to avoid cross-layer coupling —
 * the REST DTO carries Bean Validation annotations that are meaningless in a GraphQL context.
 */
public record CreateProductInput(
        String name,
        String description,
        Double price,
        String imageUrl,
        Long categoryId
) {
}
