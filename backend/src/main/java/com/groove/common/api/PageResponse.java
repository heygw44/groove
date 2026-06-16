package com.groove.common.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 페이징 응답 표준 envelope. Spring Data Page 를 매핑해 응답한다.
 * from(Page, Function) 으로 도메인 → 응답 DTO 변환을 함께 수행한다.
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

    public static <S, T> PageResponse<T> from(Page<S> source, Function<S, T> mapper) {
        List<T> content = source.getContent().stream().map(mapper).toList();
        return new PageResponse<>(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast()
        );
    }

    /** 이미 DTO 로 변환된 Page 를 그대로 envelope 에 감싼다(항등 매퍼 불필요). */
    public static <T> PageResponse<T> of(Page<T> source) {
        return new PageResponse<>(
                source.getContent(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast()
        );
    }
}
