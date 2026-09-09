package com.groove.catalog.dto;

/** 재검증 단건 적용 결과. changed 가 false 여도 discogs_synced_at 은 갱신됐다는 뜻이라 성공으로 센다. */
public record DiscogsResyncOutcome(boolean changed) {
}
