package com.groove.global.common;

import java.util.List;

/** 건수 없는 페이징 응답 포맷: { content, page, size, hasNext }. size + 1 건을 조회해 다음 페이지 유무만 판별한다. */
public record SliceResponse<T>(
		List<T> content,
		int page,
		int size,
		boolean hasNext
) {

	public static <T> SliceResponse<T> of(List<T> fetched, int page, int size) {
		boolean hasNext = fetched.size() > size;
		List<T> content = hasNext ? List.copyOf(fetched.subList(0, size)) : fetched;
		return new SliceResponse<>(content, page, size, hasNext);
	}
}
