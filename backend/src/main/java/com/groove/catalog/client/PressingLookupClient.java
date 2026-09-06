package com.groove.catalog.client;

import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.client.dto.DiscogsSearchResponse;

/** 외부 프레싱 카탈로그 조회. 구현은 Discogs 뿐이지만 서비스 계층이 외부 API 종류에 묶이지 않도록 인터페이스로 뺀다. */
public interface PressingLookupClient {

	/** page 는 0-base. barcode/catalogNo/query 는 null 이면 검색 조건에서 생략된다. */
	DiscogsSearchResponse search(String barcode, String catalogNo, String query, int page);

	DiscogsReleaseResponse getRelease(long releaseId);

	/** page 는 0-base. */
	DiscogsMasterVersionsResponse getMasterVersions(long masterId, int page);
}
