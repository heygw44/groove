package com.groove.catalog.support;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.client.dto.DiscogsSearchResponse;
import com.groove.fixture.DiscogsFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

/** 배치 테스트용 인메모리 Discogs 클라이언트. 실제 launcher 는 비동기라 스레드 세이프하게 만든다. */
public class FakePressingLookupClient implements PressingLookupClient {

	private final Map<Long, List<List<DiscogsMasterVersionsResponse.Version>>> masterPages =
			new ConcurrentHashMap<>();
	private final Map<Long, DiscogsReleaseResponse> releases = new ConcurrentHashMap<>();
	private final Set<Long> notFoundReleaseIds = ConcurrentHashMap.newKeySet();
	private final Map<Long, Integer> transientFailuresRemaining = new ConcurrentHashMap<>();
	private final Map<String, Integer> masterPageCalls = new ConcurrentHashMap<>();
	private final Map<Long, Integer> releaseCalls = new ConcurrentHashMap<>();

	public void addMaster(long masterId, List<List<DiscogsMasterVersionsResponse.Version>> pages) {
		masterPages.put(masterId, pages);
	}

	public void addRelease(DiscogsReleaseResponse release) {
		addRelease(release.id(), release);
	}

	/** 병합(리다이렉트) 시나리오용. release.id() 가 requestedId 와 달라도 requestedId 로 조회되게 등록한다. */
	public void addRelease(long requestedId, DiscogsReleaseResponse release) {
		releases.put(requestedId, release);
	}

	public void markNotFound(long id) {
		notFoundReleaseIds.add(id);
	}

	public void failTransiently(long id, int times) {
		transientFailuresRemaining.put(id, times);
	}

	public void reset() {
		masterPages.clear();
		releases.clear();
		notFoundReleaseIds.clear();
		transientFailuresRemaining.clear();
		masterPageCalls.clear();
		releaseCalls.clear();
	}

	public int masterPageCalls(long masterId, int page) {
		return masterPageCalls.getOrDefault(masterId + ":" + page, 0);
	}

	public int releaseCalls(long releaseId) {
		return releaseCalls.getOrDefault(releaseId, 0);
	}

	@Override
	public synchronized DiscogsMasterVersionsResponse getMasterVersions(long masterId, int page) {
		masterPageCalls.merge(masterId + ":" + page, 1, Integer::sum);
		List<List<DiscogsMasterVersionsResponse.Version>> pages = masterPages.get(masterId);
		if (pages == null) {
			throw new BusinessException(ErrorCode.CATALOG_RELEASE_NOT_FOUND);
		}
		return DiscogsFixture.masterVersionsResponse(page, pages.size(), pages.get(page));
	}

	@Override
	public synchronized DiscogsReleaseResponse getRelease(long releaseId) {
		releaseCalls.merge(releaseId, 1, Integer::sum);
		if (notFoundReleaseIds.contains(releaseId)) {
			throw new BusinessException(ErrorCode.CATALOG_RELEASE_NOT_FOUND);
		}
		Integer remaining = transientFailuresRemaining.get(releaseId);
		if (remaining != null && remaining != 0) {
			if (remaining > 0) {
				transientFailuresRemaining.put(releaseId, remaining - 1);
			}
			throw new BusinessException(ErrorCode.CATALOG_LOOKUP_FAILED);
		}
		DiscogsReleaseResponse release = releases.get(releaseId);
		if (release == null) {
			throw new BusinessException(ErrorCode.CATALOG_RELEASE_NOT_FOUND);
		}
		return release;
	}

	@Override
	public DiscogsSearchResponse search(String barcode, String catalogNo, String query, int page) {
		throw new UnsupportedOperationException();
	}
}
