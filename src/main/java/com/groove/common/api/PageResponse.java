package com.groove.common.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 페이징 응답 표준 envelope (API §4 {@code PageResponse<T>}).
 *
 * <p>Spring Data 의 {@link Page} 기본 직렬화는 형식이 verbose 하고 안정성이 낮아 (스키마 변경 가능성)
 * 본 envelope 으로 매핑해 응답한다. W5-2(Artist) 가 첫 도입이며 후속 페이징 엔드포인트
 * (Album / 주문 / 리뷰 등) 도 동일 형식을 따른다.
 *
 * <p>매핑 시점에 도메인 → 응답 DTO 변환을 함께 수행하기 위해
 * {@link #from(Page, Function)} 정적 팩토리를 제공한다.
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
}
