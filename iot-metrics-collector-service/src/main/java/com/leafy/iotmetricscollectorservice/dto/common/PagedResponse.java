package com.leafy.iotmetricscollectorservice.dto.common;

import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Page;

public record PagedResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalItems,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious
) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        List<T> items = page != null ? page.getContent() : Collections.emptyList();
        int pageNumber = page != null ? page.getNumber() : 0;
        int pageSize = page != null ? page.getSize() : 0;
        long totalItems = page != null ? page.getTotalElements() : 0L;
        int totalPages = page != null ? page.getTotalPages() : 0;
        boolean hasNext = page != null && page.hasNext();
        boolean hasPrevious = page != null && page.hasPrevious();

        return new PagedResponse<>(items, pageNumber, pageSize, totalItems, totalPages, hasNext, hasPrevious);
    }
}
