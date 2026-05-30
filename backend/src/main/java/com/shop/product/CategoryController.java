package com.shop.product;

import com.shop.product.dto.CategoryResponse;
import com.shop.product.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only category endpoint, public (used to populate the catalog filter UI).
 *
 * <p>Returned unpaginated on purpose: the category set is small and bounded, so a full list is
 * the simpler, more useful contract here (KISS) — a justified exception to the "paginate all
 * lists" rule. No dedicated service layer for the same reason: the logic is a trivial
 * repository read + mapping, and a pass-through service would be over-engineering.</p>
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryController(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        log.info("GET /api/categories");
        return categoryMapper.toResponseList(categoryRepository.findAll(Sort.by("name")));
    }
}
