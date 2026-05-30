package com.shop.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

/**
 * Payload to update a product (PUT — full replacement). Same fields and validation rules as
 * {@link ProductCreateRequest}; kept as a separate type so the create/update contracts can
 * diverge later without coupling.
 *
 * @param name        product name (required, ≤255)
 * @param description optional long description (≤4000)
 * @param price       price ≥ 0, up to 10 integer + 2 fraction digits (matches NUMERIC(12,2))
 * @param imageUrl    optional valid URL (≤1024)
 * @param categoryId  optional existing category id (positive)
 */
public record ProductUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 4000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Size(max = 1024) @URL String imageUrl,
        @Positive Long categoryId
) {
}
