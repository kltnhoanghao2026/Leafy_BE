package com.leafy.iottestdataservice.client.dto;

import java.util.List;

public record CollectorPagedResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalItems,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious
) {
}
