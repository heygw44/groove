package com.groove.limited.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.groove.global.common.PageLimits;
import com.groove.limited.entity.LimitedDropStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record AdminLimitedDropSearchRequest(
		LimitedDropStatus status,
		@PositiveOrZero @Max(PageLimits.MAX_PAGE) Integer page,
		@Min(1) @Max(100) Integer size
) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;

	public Pageable toPageable() {
		int resolvedPage = page == null ? DEFAULT_PAGE : page;
		int resolvedSize = size == null ? DEFAULT_SIZE : size;
		return PageRequest.of(resolvedPage, resolvedSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
	}
}
