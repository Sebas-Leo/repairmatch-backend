package com.repairmatch.repairmatch_backend.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope for paginated lists: {@code content} plus the metadata a client needs
 * to render "showing X-Y of Z" and page controls. Pages are zero-based.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** Validates the requested window; answers 400 instead of silently clamping. */
    public static PageRequest request(int page, int size, Sort sort) {
        // The offset must fit in an int: Spring Data narrows it before querying.
        if (page < 0 || size < 1 || size > MAX_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid page or size: page must be >= 0 and size between 1 and " + MAX_SIZE);
        }
        return PageRequest.of(page, size, sort);
    }

    public static <E, T> PageResponse<T> from(Page<E> source, Function<E, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast());
    }

    /** Pages an already computed, already ordered list (for results filtered in memory). */
    public static <T> PageResponse<T> slice(List<T> all, PageRequest request) {
        int size = request.getPageSize();
        long total = all.size();
        int from = (int) Math.min(request.getOffset(), total);
        int to = (int) Math.min(from + (long) size, total);
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(
                List.copyOf(all.subList(from, to)),
                request.getPageNumber(),
                size,
                total,
                totalPages,
                request.getPageNumber() == 0,
                request.getPageNumber() >= totalPages - 1);
    }
}
