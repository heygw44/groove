package com.groove.catalog.batch;

import lombok.Getter;

/** 일시적 실패(레이트리밋, 통신 오류 등). Step 의 retry 대상. */
@Getter
public class CatalogTransientException extends RuntimeException {

	private final long discogsReleaseId;

	public CatalogTransientException(long discogsReleaseId, String message, Throwable cause) {
		super(message, cause);
		this.discogsReleaseId = discogsReleaseId;
	}

	public CatalogTransientException(long discogsReleaseId, String message) {
		super(message);
		this.discogsReleaseId = discogsReleaseId;
	}
}
