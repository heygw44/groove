package com.groove.catalog.batch;

import lombok.Getter;

/** 특정 릴리즈 하나만 실패한 경우. Step 의 skip 대상. */
@Getter
public class CatalogItemException extends RuntimeException {

	private final long discogsReleaseId;

	public CatalogItemException(long discogsReleaseId, String message, Throwable cause) {
		super(message, cause);
		this.discogsReleaseId = discogsReleaseId;
	}

	public CatalogItemException(long discogsReleaseId, String message) {
		super(message);
		this.discogsReleaseId = discogsReleaseId;
	}
}
