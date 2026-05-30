package com.shop.product.repository;

import com.shop.product.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link Category}.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
