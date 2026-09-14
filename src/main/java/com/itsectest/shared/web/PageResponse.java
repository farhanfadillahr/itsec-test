package com.itsectest.shared.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PageResponse", description = "One page of results")
public record PageResponse<T>(
        List<T> items,
        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "137") long totalItems,
        @Schema(example = "7") int totalPages,
        @Schema(example = "false") boolean last) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
