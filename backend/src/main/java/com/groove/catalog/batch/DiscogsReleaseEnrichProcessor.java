package com.groove.catalog.batch;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.batch.item.ItemProcessor;

import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse.Version;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.dto.CatalogImportItem;
import com.groove.catalog.service.DiscogsReleaseMapper;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.product.entity.Genre;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.ProductRepository;

import lombok.extern.slf4j.Slf4j;

/** 마스터 버전 하나를 상세 조회해 상품 등록용 아이템으로 변환한다. */
@Slf4j
public class DiscogsReleaseEnrichProcessor implements ItemProcessor<Version, CatalogImportItem> {

	private final PressingLookupClient client;
	private final ProductRepository productRepository;
	private final GenreRepository genreRepository;
	private final DiscogsReleaseMapper mapper;
	private final long masterId;
	private final BigDecimal defaultPrice;

	private List<String> knownGenreNames;

	public DiscogsReleaseEnrichProcessor(PressingLookupClient client, ProductRepository productRepository,
			GenreRepository genreRepository, DiscogsReleaseMapper mapper, long masterId, BigDecimal defaultPrice) {
		this.client = client;
		this.productRepository = productRepository;
		this.genreRepository = genreRepository;
		this.mapper = mapper;
		this.masterId = masterId;
		this.defaultPrice = defaultPrice;
	}

	@Override
	public CatalogImportItem process(Version version) {
		if (!mapper.isVinylFormat(version.format())) {
			log.debug("바이닐 포맷이 아니라 건너뜀: releaseId={} format={}", version.id(), version.format());
			return null;
		}
		if (productRepository.existsByDiscogsReleaseId(version.id())) {
			return null;
		}

		DiscogsReleaseResponse release = fetchRelease(version.id());
		if (!mapper.isVinyl(release)) {
			return null;
		}

		CatalogImportItem item = mapper.toImportItem(release, knownGenreNames(), masterId, defaultPrice);
		if (isBlank(item.title()) || isBlank(item.artistName())) {
			throw new CatalogItemException(version.id(), "제목 또는 아티스트명이 비어 있습니다.");
		}
		return item;
	}

	private DiscogsReleaseResponse fetchRelease(long releaseId) {
		try {
			return client.getRelease(releaseId);
		} catch (BusinessException e) {
			if (e.getErrorCode() == ErrorCode.CATALOG_RELEASE_NOT_FOUND) {
				throw new CatalogItemException(releaseId, "릴리즈를 찾을 수 없습니다.", e);
			}
			if (e.getErrorCode() == ErrorCode.CATALOG_RATE_LIMITED
					|| e.getErrorCode() == ErrorCode.CATALOG_LOOKUP_FAILED) {
				throw new CatalogTransientException(releaseId, "일시적으로 조회에 실패했습니다.", e);
			}
			throw e;
		}
	}

	private List<String> knownGenreNames() {
		if (knownGenreNames == null) {
			knownGenreNames = genreRepository.findAllByOrderByNameAsc().stream().map(Genre::getName).toList();
		}
		return knownGenreNames;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
