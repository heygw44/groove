package com.groove.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;

import java.util.List;
import java.util.function.Function;

/**
 * keyset(커서) 페이징 응답 표준 envelope. totalElements/totalPages 없이 다음 페이지 존재 여부
 * (hasNext)와 다음 요청에 쓸 불투명 커서(nextCursor)를 제공한다.
 * 마지막 페이지면 hasNext=false, nextCursor=null.
 */
public record ScrollResponse<T>(
        @Schema(description = "이번 페이지 응답 목록") List<T> content,
        @Schema(description = "이번 페이지 요소 수", example = "20") int size,
        @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext,
        @Schema(description = "다음 페이지 커서(없으면 null)", nullable = true) String nextCursor
) {

    /**
     * 도메인 Window 를 응답 DTO 로 변환하며 envelope 에 감싼다. hasNext 일 때만
     * 마지막 요소의 keyset 위치를 인코딩해 nextCursor 로 내려준다.
     */
    public static <S, T> ScrollResponse<T> from(Window<S> window, Function<S, T> mapper, CursorCodec codec, Sort sort) {
        List<T> content = window.getContent().stream().map(mapper).toList();
        String nextCursor = (window.hasNext() && !window.isEmpty())
                ? codec.encode(window.positionAt(window.size() - 1), sort)
                : null;
        return new ScrollResponse<>(content, content.size(), window.hasNext(), nextCursor);
    }

    /** 이미 DTO 로 변환된 Window 를 그대로 감싼다(항등 매퍼 불필요). */
    public static <T> ScrollResponse<T> from(Window<T> window, CursorCodec codec, Sort sort) {
        return from(window, Function.identity(), codec, sort);
    }
}
