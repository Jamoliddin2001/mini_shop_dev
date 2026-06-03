package com.shop.product.graphql.input;

/**
 * GraphQL input type for {@code mutation { updateProduct(id: ID!, input: UpdateProductInput!) }}.
 */
public record UpdateProductInput(
        String name,
        String description,
        Double price,
        String imageUrl,
        Long categoryId
) {
}
