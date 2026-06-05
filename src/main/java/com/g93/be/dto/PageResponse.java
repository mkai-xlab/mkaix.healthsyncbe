package com.g93.be.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/**
 * A generic response wrapper for paginated data.
 *
 * @param <T> The type of the content in the page
 */
public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast
) {
    /**
     * Helper method to map a Spring Data Page to our PageResponse.
     * 
     * @param page The Spring Data Page object
     * @param <T> The type of elements in the page
     * @return A standard PageResponse record
     */
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
