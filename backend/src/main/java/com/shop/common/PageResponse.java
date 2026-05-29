package com.shop.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable, framework-agnostic pagination envelope returned at the API boundary.
 *
 * <p>We deliberately do NOT serialize Spring's {@code Page}/{@code PageImpl} directly: its JSON
 * shape is unstable across Spring versions and leaks framework internals (a violation of the
 * "DTO at the boundary" rule). This record exposes only the fields a client needs.</p>
 *
 * @param content       page items already mapped to response DTOs
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total number of matching rows across all pages
 * @param totalPages    total number of pages for the current size
 * @param <T>           response item type
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /** Wraps a Spring {@link Page} as-is (content type already matches {@code T}). */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /** Wraps a Spring {@link Page} of entities, mapping each element with {@code mapper}. */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> content = page.getContent().stream().<T>map(mapper).toList();
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
