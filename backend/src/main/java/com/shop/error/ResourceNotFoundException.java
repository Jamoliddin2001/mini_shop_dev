package com.shop.error;

/**
 * Thrown when a requested or referenced resource does not exist. Maps to HTTP 404 in
 * {@link GlobalExceptionHandler}. Generic by design so it covers any aggregate (product,
 * category, …) without a per-type exception class.
 *
 * <p>Examples: {@code GET /api/products/{id}} for a missing id, or a create/update payload that
 * references a non-existent {@code categoryId}.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * @param resourceType human-readable resource name, e.g. {@code "Product"} or {@code "Category"}
     * @param id           the identifier that was not found
     */
    public ResourceNotFoundException(String resourceType, Object id) {
        super(resourceType + " " + id + " not found");
    }
}
