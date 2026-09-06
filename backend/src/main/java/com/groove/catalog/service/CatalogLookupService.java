package com.groove.catalog.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.client.dto.DiscogsSearchResponse;
import com.groove.catalog.dto.CatalogLookupRequest;
import com.groove.catalog.dto.CatalogLookupResponse;
import com.groove.catalog.dto.CatalogReleaseDetailResponse;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.product.entity.Genre;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/** 관리자 등록 폼 프리필용 Discogs 조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CatalogLookupService {

	private static final int PAGE_SIZE = 20;

	private final PressingLookupClient client;
	private final ProductRepository productRepository;
	private final GenreRepository genreRepository;
	private final DiscogsReleaseMapper mapper;

	public PageResponse<CatalogLookupResponse> lookup(CatalogLookupRequest request) {
		if (!request.hasAnyCriteria()) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		int page = request.pageOrDefault();
		DiscogsSearchResponse response = client.search(request.barcode(), request.catalogNo(), request.query(), page);

		List<DiscogsSearchResponse.Result> results = response.results() == null ? List.of() : response.results();
		List<Long> releaseIds = results.stream().map(DiscogsSearchResponse.Result::id).toList();
		Set<Long> alreadyImportedIds = releaseIds.isEmpty() ? Set.of()
				: Set.copyOf(productRepository.findExistingDiscogsReleaseIds(releaseIds));

		List<CatalogLookupResponse> content = results.stream()
				.map(result -> mapper.toLookup(result, alreadyImportedIds.contains(result.id())))
				.toList();
		long totalElements = response.pagination() == null ? content.size() : response.pagination().items();
		return PageResponse.of(content, page, PAGE_SIZE, totalElements);
	}

	public CatalogReleaseDetailResponse getRelease(long discogsReleaseId) {
		DiscogsReleaseResponse release = client.getRelease(discogsReleaseId);
		List<String> knownGenreNames = genreRepository.findAllByOrderByNameAsc().stream().map(Genre::getName).toList();
		return mapper.toDetail(release, knownGenreNames);
	}
}
