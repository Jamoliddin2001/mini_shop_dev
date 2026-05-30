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
 * Payload to create a product. Validated at the controller boundary ({@code @Valid}).
 *
 * <p>Image is accepted as a URL (not a file upload): for a test mini-shop this avoids object
 * storage, upload security and serving concerns, and is trivially upgradable to uploads later
 * (see README → "Дальнейшее масштабирование"). {@code categoryId} is optional; when present it
 * must reference an existing category (enforced in the service → 404 otherwise).</p>
 *
 * @param name        product name (required, ≤255)
 * @param description optional long description (≤4000)
 * @param price       price ≥ 0, up to 10 integer + 2 fraction digits (matches NUMERIC(12,2))
 * @param imageUrl    optional valid URL (≤1024)
 * @param categoryId  optional existing category id (positive)
 */
public record ProductCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 4000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Size(max = 1024) @URL String imageUrl,
        @Positive Long categoryId
) {
}
