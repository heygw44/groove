package com.groove.catalog.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.dto.DiscogsResyncFields;
import com.groove.catalog.dto.DiscogsResyncOutcome;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.product.entity.Album;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Discogs 재검증 응답을 상품 한 건에 적용한다. HTTP 호출은 이 클래스 밖(스케줄러)에서 이미 끝난 뒤라
 * 여기는 순수하게 DB 트랜잭션 하나만 연다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscogsResyncService {

	private final ProductRepository productRepository;
	private final DiscogsReleaseMapper discogsReleaseMapper;
	private final Clock clock;

	/** Product.applyDiscogsSync 다섯 필드만 갱신한다. title·price·description 은 시그니처에 없어 건드릴 수 없다. */
	@Transactional
	public DiscogsResyncOutcome apply(Long productId, Long requestedReleaseId, DiscogsReleaseResponse release) {
		Product product = findProduct(productId);

		warnIfMasterChanged(product, release);
		applyRedirectIfSafe(product, requestedReleaseId, release);

		DiscogsResyncFields fields = discogsReleaseMapper.toResyncFields(release);
		List<String> changedFields = detectChangedFields(product, fields);

		product.applyDiscogsSync(fields.country(), fields.pressingYear(), fields.catalogNo(), fields.barcode(),
				fields.editionType(), LocalDateTime.now(clock));

		if (!changedFields.isEmpty()) {
			log.info("Discogs 재검증으로 상품 정보가 갱신됐다 productId={} discogsReleaseId={} changedFields={}",
					productId, product.getDiscogsReleaseId(), changedFields);
		}
		return new DiscogsResyncOutcome(!changedFields.isEmpty());
	}

	/** 릴리즈가 삭제됐을 때(404) 참조를 끊는다. 그대로 두면 죽은 id 를 다음 실행에서 다시 호출해 예산을 태운다. */
	@Transactional
	public void markReleaseNotFound(Long productId, Long discogsReleaseId) {
		Product product = findProduct(productId);
		product.clearDiscogsRelease();
		log.warn("Discogs 릴리즈를 찾을 수 없어 재검증 대상에서 제외한다 productId={} discogsReleaseId={}", productId, discogsReleaseId);
	}

	private Product findProduct(Long productId) {
		return productRepository.findById(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
	}

	// 앨범 소속을 자동으로 옮기면 프레싱 목록·album_watch 구독·NEW_PRESSING 알림 대상이 조용히 바뀐다.
	// 사람이 판단할 문제라 warn 로그만 남긴다.
	private void warnIfMasterChanged(Product product, DiscogsReleaseResponse release) {
		Album album = product.getAlbum();
		Long releaseMasterId = release.masterId();
		Long albumMasterId = album.getDiscogsMasterId();
		if (releaseMasterId == null || albumMasterId == null || releaseMasterId.equals(albumMasterId)) {
			return;
		}
		log.warn("Discogs 마스터 변경을 감지했다(자동 반영하지 않음) productId={} albumId={} albumMasterId={} releaseMasterId={}",
				product.getId(), album.getId(), albumMasterId, releaseMasterId);
	}

	// RestClient 가 리다이렉트를 따라가므로 병합된 릴리즈는 요청과 다른 id 의 200 응답으로 온다.
	// uk_product_discogs_release 유니크 제약이 있어 새 id 를 이미 다른 상품이 갖고 있으면 갱신을 건너뛴다.
	private void applyRedirectIfSafe(Product product, Long requestedReleaseId, DiscogsReleaseResponse release) {
		Long newReleaseId = release.id();
		if (newReleaseId == null || newReleaseId.equals(requestedReleaseId)) {
			return;
		}
		if (productRepository.existsByDiscogsReleaseId(newReleaseId)) {
			log.warn("Discogs 릴리즈 병합을 감지했으나 새 id 를 다른 상품이 이미 갖고 있어 건너뛴다 productId={} oldReleaseId={} "
					+ "newReleaseId={}", product.getId(), requestedReleaseId, newReleaseId);
			return;
		}
		product.changeDiscogsReleaseId(newReleaseId);
		log.info("Discogs 릴리즈 병합으로 참조 id 를 갱신했다 productId={} oldReleaseId={} newReleaseId={}",
				product.getId(), requestedReleaseId, newReleaseId);
	}

	private List<String> detectChangedFields(Product product, DiscogsResyncFields fields) {
		List<String> changed = new ArrayList<>();
		if (!Objects.equals(product.getCountry(), fields.country())) {
			changed.add("country");
		}
		if (!Objects.equals(product.getPressingYear(), fields.pressingYear())) {
			changed.add("pressingYear");
		}
		if (!Objects.equals(product.getCatalogNo(), fields.catalogNo())) {
			changed.add("catalogNo");
		}
		if (!Objects.equals(product.getBarcode(), fields.barcode())) {
			changed.add("barcode");
		}
		if (product.getEditionType() != fields.editionType()) {
			changed.add("editionType");
		}
		return changed;
	}
}
