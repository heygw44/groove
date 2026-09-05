package com.groove.product.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.inventory.service.StockService;
import com.groove.product.dto.AdminProductResponse;
import com.groove.product.dto.AdminProductSummaryResponse;
import com.groove.product.dto.ProductCreateRequest;
import com.groove.product.dto.ProductUpdateRequest;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.LabelRepository;
import com.groove.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/** 관리자 상품 등록/수정/숨김/목록 조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminProductService {

	private final ProductRepository productRepository;
	private final ArtistRepository artistRepository;
	private final LabelRepository labelRepository;
	private final GenreRepository genreRepository;
	private final StockRepository stockRepository;
	private final StockService stockService;
	private final AdminAuditLogService adminAuditLogService;

	@Transactional
	public AdminProductResponse create(Long adminId, ProductCreateRequest request) {
		Artist artist = artistRepository.findById(request.artistId())
				.orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
		Label label = findLabelOrNull(request.labelId());
		List<Genre> genres = findGenres(request.genreIds() == null ? List.of() : request.genreIds());

		Product product = Product.create(request.title(), artist, label, request.releaseDate(),
				request.pressingInfo(), request.colorVariant(), request.price(), request.description());
		genres.forEach(product::addGenre);
		addImages(product, request.imageUrls() == null ? List.of() : request.imageUrls());

		Product saved = productRepository.save(product);
		Stock stock = stockService.create(saved, request.initialStock());

		adminAuditLogService.record(adminId, AdminAuditAction.PRODUCT_CREATE, AdminAuditTargetType.PRODUCT,
				saved.getId(), null);

		return AdminProductResponse.from(saved, stock.getQuantity());
	}

	@Transactional
	public AdminProductResponse update(Long adminId, Long productId, ProductUpdateRequest request) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

		List<String> changedFields = new ArrayList<>();

		String title = coalesce(request.title(), product.getTitle(), "title", changedFields);
		Artist artist = product.getArtist();
		if (request.artistId() != null) {
			artist = artistRepository.findById(request.artistId())
					.orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
			changedFields.add("artist");
		}
		Label label = product.getLabel();
		if (request.labelId() != null && request.labelId().isPresent()) {
			label = findLabelOrNull(request.labelId().get());
			changedFields.add("label");
		}
		LocalDate releaseDate = coalesce(request.releaseDate(), product.getReleaseDate(), "releaseDate",
				changedFields);
		String pressingInfo = coalesce(request.pressingInfo(), product.getPressingInfo(), "pressingInfo",
				changedFields);
		String colorVariant = coalesce(request.colorVariant(), product.getColorVariant(), "colorVariant",
				changedFields);
		BigDecimal price = coalesce(request.price(), product.getPrice(), "price", changedFields);
		String description = coalesce(request.description(), product.getDescription(), "description",
				changedFields);

		product.updateInfo(title, artist, label, releaseDate, pressingInfo, colorVariant, price, description);

		if (request.genreIds() != null) {
			product.replaceGenres(findGenres(request.genreIds()));
			changedFields.add("genres");
		}
		if (request.imageUrls() != null) {
			product.clearImages();
			addImages(product, request.imageUrls());
			changedFields.add("images");
		}

		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

		adminAuditLogService.record(adminId, AdminAuditAction.PRODUCT_UPDATE, AdminAuditTargetType.PRODUCT,
				productId, String.join(",", changedFields));

		return AdminProductResponse.from(product, stock.getQuantity());
	}

	@Transactional
	public void hide(Long adminId, Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		product.hide();

		adminAuditLogService.record(adminId, AdminAuditAction.PRODUCT_HIDE, AdminAuditTargetType.PRODUCT, productId,
				null);
	}

	public AdminProductResponse getDetail(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

		return AdminProductResponse.from(product, stock.getQuantity());
	}

	@Transactional
	public AdminProductResponse restore(Long adminId, Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

		product.restore(stock.getQuantity());

		adminAuditLogService.record(adminId, AdminAuditAction.PRODUCT_RESTORE, AdminAuditTargetType.PRODUCT,
				productId, product.getStatus().name());

		return AdminProductResponse.from(product, stock.getQuantity());
	}

	public PageResponse<AdminProductSummaryResponse> getList(ProductStatus status, Pageable pageable) {
		Page<AdminProductSummaryResponse> page = productRepository.findAdminSummaries(status, pageable);
		return PageResponse.from(page);
	}

	private Label findLabelOrNull(Long labelId) {
		if (labelId == null) {
			return null;
		}
		return labelRepository.findById(labelId).orElseThrow(() -> new BusinessException(ErrorCode.LABEL_NOT_FOUND));
	}

	private List<Genre> findGenres(List<Long> genreIds) {
		if (genreIds.isEmpty()) {
			return List.of();
		}
		Set<Long> uniqueIds = new LinkedHashSet<>(genreIds);
		List<Genre> genres = genreRepository.findAllById(uniqueIds);
		if (genres.size() != uniqueIds.size()) {
			throw new BusinessException(ErrorCode.GENRE_NOT_FOUND);
		}
		return genres;
	}

	private void addImages(Product product, List<String> imageUrls) {
		for (int sortOrder = 0; sortOrder < imageUrls.size(); sortOrder++) {
			product.addImage(imageUrls.get(sortOrder), sortOrder);
		}
	}

	private <T> T coalesce(T newValue, T currentValue, String fieldName, List<String> changedFields) {
		if (newValue == null) {
			return currentValue;
		}
		changedFields.add(fieldName);
		return newValue;
	}
}
