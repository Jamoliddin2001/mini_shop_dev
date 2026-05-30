package com.shop.product.repository;

import com.shop.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Persistence access for {@link Product}.
 * Extends {@link JpaSpecificationExecutor} to support the dynamic catalog filter
 * (name / category / price range) added in the product phase.
 */
public interface ProductRepository
        extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
}
