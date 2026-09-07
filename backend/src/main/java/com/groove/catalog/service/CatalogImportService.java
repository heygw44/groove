package com.groove.catalog.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.dto.CatalogImportItem;
import com.groove.catalog.dto.CatalogImportRequest;
import com.groove.catalog.dto.CatalogImportResponse;
import com.groove.catalog.dto.CatalogImportResult;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.notification.service.NewPressingEvent;
import com.groove.product.entity.Genre;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/**
 * Discogs 릴리즈 단건 즉시 등록. Discogs 호출을 DB 트랜잭션 밖에서 하기 위해
 * 이 클래스는 트랜잭션을 걸지 않고, 등록/감사 로그만 {@link TransactionTemplate} 으로 묶는다.
 */
@Service
@RequiredArgsConstructor
public class CatalogImportService {

	private final PressingLookupClient client;
	private final ProductRepository productRepository;
	private final GenreRepository genreRepository;
	private final DiscogsReleaseMapper mapper;
	private final CatalogImportRegistrar registrar;
	private final AdminAuditLogService adminAuditLogService;
	private final TransactionTemplate transactionTemplate;
	private final ApplicationEventPublisher eventPublisher;

	public CatalogImportResponse importRelease(Long adminId, CatalogImportRequest request) {
		long releaseId = request.discogsReleaseId();
		if (productRepository.existsByDiscogsReleaseId(releaseId)) {
			throw new BusinessException(ErrorCode.CATALOG_ALREADY_IMPORTED);
		}

		DiscogsReleaseResponse release = client.getRelease(releaseId);
		if (!mapper.isVinyl(release)) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}

		List<String> knownGenreNames = genreRepository.findAllByOrderByNameAsc().stream()
				.map(Genre::getName)
				.toList();
		CatalogImportItem item = mapper.toImportItem(release, knownGenreNames, null, request.defaultPrice());
		if (item.title() == null || item.title().isBlank() || item.artistName() == null
				|| item.artistName().isBlank()) {
			throw new BusinessException(ErrorCode.CATALOG_LOOKUP_FAILED);
		}

		CatalogImportResult result = transactionTemplate.execute(status -> {
			CatalogImportResult registered = registrar.register(item);
			adminAuditLogService.record(adminId, AdminAuditAction.PRODUCT_IMPORT, AdminAuditTargetType.PRODUCT,
					registered.productId(), "discogsReleaseId=" + releaseId);
			eventPublisher.publishEvent(new NewPressingEvent(registered.albumId(), registered.albumTitle()));
			return registered;
		});

		return new CatalogImportResponse(result.productId(), result.albumId());
	}
}
