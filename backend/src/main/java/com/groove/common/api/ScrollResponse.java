package com.groove.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;

import java.util.List;
import java.util.function.Function;

/**
 * keyset(커서) 페이징 응답 표준 envelope (#235).
 *
 * <p>offset 의 {@link PageResponse} 와 달리 {@code totalElements/totalPages} 를 내려주지 않는다 —
 * keyset 스크롤은 전체 카운트 쿼리를 하지 않는 것이 핵심 이점이기 때문이다. 대신 다음 페이지 존재 여부
 * ({@code hasNext}) 와 다음 요청에 그대로 되돌려줄 불투명 커서({@code nextCursor}) 를 제공한다.
 * 마지막 페이지면 {@code hasNext=false}, {@code nextCursor=null}.
 *
 * @param content    이번 윈도우의 응답 DTO 목록
 * @param size       이번 윈도우에 담긴 요소 수(마지막 페이지는 요청 size 보다 작을 수 있음)
 * @param hasNext    다음 페이지 존재 여부
 * @param nextCursor 다음 페이지 요청에 쓸 불투명 커서(없으면 null)
 */
public record ScrollResponse<T>(
        @Schema(description = "이번 페이지 응답 목록") List<T> content,
        @Schema(description = "이번 페이지 요소 수", example = "20") int size,
        @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext,
        @Schema(description = "다음 페이지 커서(없으면 null)", nullable = true) String nextCursor
) {

    /**
     * 도메인 {@link Window} 를 응답 DTO 로 변환하며 envelope 에 감싼다. {@code hasNext} 일 때만
     * 마지막 요소의 keyset 위치를 인코딩해 {@code nextCursor} 로 내려준다. positions 는 {@code map}
     * 변환 후에도 인덱스 기준으로 보존되므로, 호출 측이 이미 변환을 마친 윈도우에도 적용된다.
     */
    public static <S, T> ScrollResponse<T> from(Window<S> window, Function<S, T> mapper, CursorCodec codec, Sort sort) {
        List<T> content = window.getContent().stream().map(mapper).toList();
        String nextCursor = (window.hasNext() && !window.isEmpty())
                ? codec.encode(window.positionAt(window.size() - 1), sort)
                : null;
        return new ScrollResponse<>(content, content.size(), window.hasNext(), nextCursor);
    }

    /** 호출 측이 트랜잭션 안에서 이미 DTO 로 변환한 {@link Window} 를 그대로 감싼다(항등 매퍼 강요 회피). */
    public static <T> ScrollResponse<T> from(Window<T> window, CursorCodec codec, Sort sort) {
        return from(window, Function.identity(), codec, sort);
    }
}
