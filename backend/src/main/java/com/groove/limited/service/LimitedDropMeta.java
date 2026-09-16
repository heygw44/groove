package com.groove.limited.service;

import java.time.LocalDateTime;

import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;

/** 구매 진입점이 필요로 하는 드롭 상태의 스냅샷. {@link LimitedDropMetaCache} 가 TTL 동안 들고 있는다. */
public record LimitedDropMeta(Long id, LimitedDropStatus status, LocalDateTime openAt, LocalDateTime closeAt,
		Long productId) {

	public static LimitedDropMeta from(LimitedDrop drop) {
		return new LimitedDropMeta(drop.getId(), drop.getStatus(), drop.getOpenAt(), drop.getCloseAt(),
				drop.getProduct().getId());
	}

	public void validatePurchasable(LocalDateTime now) {
		LimitedDrop.checkPurchasable(status, openAt, closeAt, now);
	}
}
