package com.groove.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/** GET /admin/stats/limited-drops 페이징 조건. 정렬은 open_at DESC, id DESC 로 고정한다. */
public record LimitedDropStatsRequest(
		@PositiveOrZero Integer page,
		@Min(1) @Max(100) Integer size
) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;

	public int resolvedPage() {
		return page == null ? DEFAULT_PAGE : page;
	}

	public int resolvedSize() {
		return size == null ? DEFAULT_SIZE : size;
	}

	public int offset() {
		return resolvedPage() * resolvedSize();
	}
}
