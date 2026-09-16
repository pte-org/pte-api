package com.pte.shared.web;

/** Common offset-pagination metadata shared by human-facing endpoints. */
public record PageMeta(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean hasNext,
        boolean hasPrevious) {
}
