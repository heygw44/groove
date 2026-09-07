package com.groove.catalog.client;

import java.util.function.Supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.client.dto.DiscogsSearchResponse;
import com.groove.catalog.config.DiscogsProperties;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/** Discogs 개인 토큰 연동 구현. 토큰 값은 로그·예외 메시지에 절대 남기지 않는다. */
@Slf4j
@Component
public class DiscogsClient implements PressingLookupClient {

	private static final String SEARCH_PATH = "/database/search";
	private static final String RELEASE_PATH = "/releases/{id}";
	private static final String MASTER_VERSIONS_PATH = "/masters/{id}/versions";
	private static final String RATE_LIMIT_REMAINING_HEADER = "X-Discogs-Ratelimit-Remaining";
	private static final String RETRY_AFTER_HEADER = "Retry-After";
	private static final int PER_PAGE = 20;
	private static final long DEFAULT_RETRY_AFTER_SECONDS = 60;

	private final RestClient restClient;
	private final DiscogsRateLimiter rateLimiter;
	private final Sleeper sleeper;
	private final DiscogsProperties.Retry retry;

	public DiscogsClient(RestClient discogsRestClient, DiscogsRateLimiter rateLimiter, Sleeper sleeper,
			DiscogsProperties properties) {
		this.restClient = discogsRestClient;
		this.rateLimiter = rateLimiter;
		this.sleeper = sleeper;
		this.retry = properties.retry();
	}

	@Override
	public DiscogsSearchResponse search(String barcode, String catalogNo, String query, int page) {
		return call("search", () -> restClient.get()
				.uri(uriBuilder -> {
					uriBuilder.path(SEARCH_PATH)
							.queryParam("type", "release")
							.queryParam("page", page + 1)
							.queryParam("per_page", PER_PAGE);
					if (barcode != null && !barcode.isBlank()) {
						uriBuilder.queryParam("barcode", barcode);
					}
					if (catalogNo != null && !catalogNo.isBlank()) {
						uriBuilder.queryParam("catno", catalogNo);
					}
					if (query != null && !query.isBlank()) {
						uriBuilder.queryParam("q", query);
					}
					return uriBuilder.build();
				})
				.retrieve()
				.toEntity(DiscogsSearchResponse.class), ErrorCode.CATALOG_LOOKUP_FAILED);
	}

	@Override
	public DiscogsReleaseResponse getRelease(long releaseId) {
		return call("getRelease", () -> restClient.get()
				.uri(RELEASE_PATH, releaseId)
				.retrieve()
				.toEntity(DiscogsReleaseResponse.class), ErrorCode.CATALOG_RELEASE_NOT_FOUND);
	}

	@Override
	public DiscogsMasterVersionsResponse getMasterVersions(long masterId, int page) {
		return call("getMasterVersions", () -> restClient.get()
				.uri(uriBuilder -> uriBuilder.path(MASTER_VERSIONS_PATH)
						.queryParam("page", page + 1)
						.queryParam("per_page", PER_PAGE)
						.build(masterId))
				.retrieve()
				.toEntity(DiscogsMasterVersionsResponse.class), ErrorCode.CATALOG_RELEASE_NOT_FOUND);
	}

	private <T> T call(String operation, Supplier<ResponseEntity<T>> request, ErrorCode notFoundErrorCode) {
		int attempt = 0;
		while (true) {
			attempt++;
			try {
				return execute(request, notFoundErrorCode);
			} catch (RestClientException ex) {
				if (attempt >= retry.maxAttempts() || !isRetryable(ex)) {
					throw toLookupFailed(operation, attempt, ex);
				}
				log.warn("Discogs 일시 오류로 재시도합니다: operation={} attempt={}/{} type={} message={}",
						operation, attempt, retry.maxAttempts(), ex.getClass().getSimpleName(), ex.getMessage());
				sleeper.sleep(retry.backoff());
			}
		}
	}

	private <T> T execute(Supplier<ResponseEntity<T>> request, ErrorCode notFoundErrorCode) {
		rateLimiter.acquire();
		try {
			ResponseEntity<T> response = request.get();
			updateRemaining(response);
			T body = response.getBody();
			if (body == null) {
				throw new BusinessException(ErrorCode.CATALOG_LOOKUP_FAILED, "DISCOGS 응답 본문이 비어 있습니다.");
			}
			return body;
		} catch (HttpClientErrorException.NotFound ex) {
			throw new BusinessException(notFoundErrorCode, "DISCOGS 404: 리소스를 찾을 수 없습니다.");
		} catch (HttpClientErrorException.TooManyRequests ex) {
			long retryAfter = retryAfterSeconds(ex);
			rateLimiter.blockFor(retryAfter);
			log.warn("Discogs 레이트리밋 초과: retryAfter={}", retryAfter);
			throw new BusinessException(ErrorCode.CATALOG_RATE_LIMITED);
		}
	}

	private boolean isRetryable(RestClientException ex) {
		return ex instanceof ResourceAccessException || ex instanceof HttpServerErrorException;
	}

	private BusinessException toLookupFailed(String operation, int attempts, RestClientException ex) {
		if (ex instanceof RestClientResponseException responseException) {
			log.warn("Discogs API 오류 응답: operation={} attempts={} status={}",
					operation, attempts, responseException.getStatusCode());
			return new BusinessException(ErrorCode.CATALOG_LOOKUP_FAILED,
					"DISCOGS " + responseException.getStatusCode() + " 응답을 받았습니다.");
		}
		log.warn("Discogs API 통신 실패: operation={} attempts={}", operation, attempts, ex);
		return new BusinessException(ErrorCode.CATALOG_LOOKUP_FAILED, "DISCOGS 통신 실패: " + ex.getMessage());
	}

	private void updateRemaining(ResponseEntity<?> response) {
		String remaining = response.getHeaders().getFirst(RATE_LIMIT_REMAINING_HEADER);
		if (remaining == null) {
			return;
		}
		try {
			rateLimiter.updateRemaining(Integer.parseInt(remaining.trim()));
		} catch (NumberFormatException ex) {
			log.warn("Discogs 레이트리밋 헤더 파싱 실패: value={}", remaining);
		}
	}

	private long retryAfterSeconds(HttpClientErrorException.TooManyRequests ex) {
		String retryAfter = ex.getResponseHeaders() == null ? null
				: ex.getResponseHeaders().getFirst(RETRY_AFTER_HEADER);
		if (retryAfter == null) {
			return DEFAULT_RETRY_AFTER_SECONDS;
		}
		try {
			return Long.parseLong(retryAfter.trim());
		} catch (NumberFormatException ex2) {
			return DEFAULT_RETRY_AFTER_SECONDS;
		}
	}
}
