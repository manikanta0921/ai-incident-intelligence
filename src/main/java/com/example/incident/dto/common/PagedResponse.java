package com.example.incident.dto.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stable pagination envelope around Spring's Page.
 *
 * Why not return Page<T> directly? Serializing PageImpl as-is produces a
 * deprecation warning in Spring Boot 3.x and ties the JSON shape to a
 * framework class. This DTO keeps the API contract under our control.
 */
@Schema(description = "Paginated result wrapper")
public record PagedResponse<T>(

        @Schema(description = "Page content")
        List<T> content,

        @Schema(example = "0")
        int page,

        @Schema(example = "10")
        int size,

        @Schema(example = "57")
        long totalElements,

        @Schema(example = "6")
        int totalPages,

        @Schema(example = "true")
        boolean last
) {

    /** Maps a Spring Page of entities to a PagedResponse of DTOs. */
    public static <E, D> PagedResponse<D> from(Page<E> page, Function<E, D> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    /** Wraps a page that is already made of DTOs (mapping already done in the service). */
    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
