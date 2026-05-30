package com.shop.product.spec;

import com.shop.product.domain.Product;
import com.shop.product.dto.ProductFilter;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link ProductSpecifications} factory. Verifies the Strategy contract at the
 * composition level (without a database): absent criteria produce {@code null} and drop out, while
 * present criteria and {@link ProductSpecifications#build} produce a usable specification. The
 * actual SQL semantics (ILIKE, fetch-join, ranges) are exercised in the integration test.
 */
class ProductSpecificationsTest {

    @Test
    void blankOrNullNameProducesNoCriterion() {
        assertThat(ProductSpecifications.nameContains(null)).isNull();
        assertThat(ProductSpecifications.nameContains("   ")).isNull();
    }

    @Test
    void presentNameProducesACriterion() {
        assertThat(ProductSpecifications.nameContains("phone")).isNotNull();
    }

    @Test
    void nullScalarsProduceNoCriterion() {
        assertThat(ProductSpecifications.hasCategory(null)).isNull();
        assertThat(ProductSpecifications.priceGte(null)).isNull();
        assertThat(ProductSpecifications.priceLte(null)).isNull();
    }

    @Test
    void presentScalarsProduceCriteria() {
        assertThat(ProductSpecifications.hasCategory(1L)).isNotNull();
        assertThat(ProductSpecifications.priceGte(new BigDecimal("1.00"))).isNotNull();
        assertThat(ProductSpecifications.priceLte(new BigDecimal("9.99"))).isNotNull();
    }

    @Test
    void buildAlwaysReturnsSpecificationEvenForEmptyFilter() {
        Specification<Product> spec = ProductSpecifications.build(new ProductFilter(null, null, null, null));
        assertThat(spec).isNotNull(); // includes the category fetch-join even with no predicates
    }
}
