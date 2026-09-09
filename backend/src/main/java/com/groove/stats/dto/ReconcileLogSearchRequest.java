package com.groove.stats.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/** 대사 로그 목록 조회 조건. 정렬은 최근순(createdAt DESC, id DESC)으로 고정한다. */
public record ReconcileLogSearchRequest(
		@PositiveOrZero Integer page,
		@Min(1) @Max(100) Integer size,
		Boolean repaired
) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;

	public Pageable toPageable() {
		int resolvedPage = page == null ? DEFAULT_PAGE : page;
		int resolvedSize = size == null ? DEFAULT_SIZE : size;
		return PageRequest.of(resolvedPage, resolvedSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
	}
}
