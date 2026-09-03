package com.groove.review.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReviewListRequest(
		String sort,
		@Min(0) Integer page,
		@Min(1) @Max(100) Integer size
) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 10;

	public Pageable toPageable() {
		int resolvedPage = page == null ? DEFAULT_PAGE : page;
		int resolvedSize = size == null ? DEFAULT_SIZE : size;
		return PageRequest.of(resolvedPage, resolvedSize, ReviewSortType.from(sort).toSort());
	}
}
