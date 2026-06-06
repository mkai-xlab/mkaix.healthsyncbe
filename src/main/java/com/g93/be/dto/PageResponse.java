package com.g93.be.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/**
 * A generic response wrapper for paginated API results.
 *
 * @param <T>           The type of the content items.
 * @param content       The list of items for the current page.
 * @param pageNumber    The current page number (0-indexed).
 * @param pageSize      The number of items per page.
 * @param totalElements The total number of items across all pages.
 * @param totalPages    The total number of pages.
 * @param isLast        True if this is the last page.
 */
public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
