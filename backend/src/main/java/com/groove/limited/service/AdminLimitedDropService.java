package com.groove.limited.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.inventory.dto.StockAdjustRequest;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.service.StockService;
import com.groove.limited.dto.AdminLimitedDropDetailResponse;
import com.groove.limited.dto.AdminLimitedDropResponse;
import com.groove.limited.dto.AdminLimitedDropSummaryResponse;
import com.groove.limited.dto.LimitedDropCreateRequest;
import com.groove.limited.dto.LimitedDropUpdateRequest;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.entity.LimitedPurchase;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/** 관리자 한정반 드롭 등록/수정/오픈/마감/목록 조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminLimitedDropService {

	private static final String CREATE_STOCK_REASON = "한정반 드롭 등록";
	private static final String UPDATE_STOCK_REASON = "한정반 드롭 수정";

	private final LimitedDropRepository limitedDropRepository;
	private final LimitedPurchaseRepository limitedPurchaseRepository;
	private final ProductRepository productRepository;
	private final StockService stockService;
	private final LimitedDropRedisService limitedDropRedisService;
	private final LimitedDropStatFlusher limitedDropStatFlusher;
	private final AdminAuditLogService adminAuditLogService;

	@Transactional
	public AdminLimitedDropResponse create(Long adminId, LimitedDropCreateRequest request) {
		Product product = productRepository.findById(request.productId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

		// 유니크 제약(uk_limited_drop_product)이 CLOSED 드롭도 포함하므로 상태와 무관하게 재등록을 막는다.
		if (limitedDropRepository.findByProductId(product.getId()).isPresent()) {
			throw new BusinessException(ErrorCode.LIMITED_DROP_ALREADY_EXISTS);
		}

		LimitedDrop drop = LimitedDrop.schedule(product, request.totalQuantity(), request.perMemberLimit(),
				request.openAt(), request.closeAt());
		LimitedDrop saved = limitedDropRepository.save(drop);

		stockService.adjust(product.getId(),
				new StockAdjustRequest(StockChangeType.ADJUST, saved.getTotalQuantity(), CREATE_STOCK_REASON));

		adminAuditLogService.record(adminId, AdminAuditAction.LIMITED_DROP_CREATE, AdminAuditTargetType.LIMITED_DROP,
				saved.getId(), null);

		return AdminLimitedDropResponse.from(saved);
	}

	@Transactional
	public AdminLimitedDropResponse update(Long adminId, Long dropId, LimitedDropUpdateRequest request) {
		LimitedDrop drop = limitedDropRepository.findWithProductById(dropId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LIMITED_DROP_NOT_FOUND));

		List<String> changedFields = new ArrayList<>();
		int totalQuantity = coalesce(request.totalQuantity(), drop.getTotalQuantity(), "totalQuantity",
				changedFields);
		int perMemberLimit = coalesce(request.perMemberLimit(), drop.getPerMemberLimit(), "perMemberLimit",
				changedFields);
		LocalDateTime openAt = coalesce(request.openAt(), drop.getOpenAt(), "openAt", changedFields);
		LocalDateTime closeAt = coalesce(request.closeAt(), drop.getCloseAt(), "closeAt", changedFields);

		drop.reschedule(totalQuantity, perMemberLimit, openAt, closeAt);

		if (changedFields.contains("totalQuantity")) {
			stockService.adjust(drop.getProduct().getId(),
					new StockAdjustRequest(StockChangeType.ADJUST, drop.getTotalQuantity(), UPDATE_STOCK_REASON));
		}

		adminAuditLogService.record(adminId, AdminAuditAction.LIMITED_DROP_UPDATE, AdminAuditTargetType.LIMITED_DROP,
				dropId, String.join(",", changedFields));

		return AdminLimitedDropResponse.from(drop);
	}

	@Transactional
	public AdminLimitedDropResponse open(Long adminId, Long dropId) {
		LimitedDrop drop = limitedDropRepository.findWithProductById(dropId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LIMITED_DROP_NOT_FOUND));

		// 이미 OPEN 이면 SET NX 만 다시 쳐서 키가 유실됐을 때 DB 기준 남은 수량으로 복구하고, 감사 로그는 남기지 않는다.
		if (drop.getStatus() == LimitedDropStatus.OPEN) {
			limitedDropRedisService.initStock(drop.getId(), drop.remainingQuantity());
			return AdminLimitedDropResponse.from(drop);
		}

		// 상태 검증 뒤에 Redis 를 치므로 실패 시 트랜잭션이 롤백되고 CLOSED 드롭에 키가 생기지 않는다.
		drop.open();
		limitedDropRedisService.initStock(drop.getId(), drop.remainingQuantity());

		adminAuditLogService.record(adminId, AdminAuditAction.LIMITED_DROP_OPEN, AdminAuditTargetType.LIMITED_DROP,
				dropId, "SCHEDULED->OPEN");

		return AdminLimitedDropResponse.from(drop);
	}

	@Transactional
	public AdminLimitedDropResponse close(Long adminId, Long dropId) {
		// 스케줄러 close 와 동시에 마감되면 stat 이 두 번 INSERT 될 수 있어 findWithProductById 대신 락을 잡는다.
		LimitedDrop drop = limitedDropRepository.findByIdForUpdate(dropId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LIMITED_DROP_NOT_FOUND));

		if (drop.getStatus() == LimitedDropStatus.CLOSED) {
			// 빈 집계는 flushAndClear 가 안전하게 건너뛰고, flush 가 밀렸던 경우의 복구 경로가 된다.
			limitedDropStatFlusher.flushAndClear(drop);
			return AdminLimitedDropResponse.from(drop);
		}

		LimitedDropStatus previous = drop.getStatus();
		// DB 재고는 남은 수량을 그대로 보존한다.
		drop.close();

		adminAuditLogService.record(adminId, AdminAuditAction.LIMITED_DROP_CLOSE, AdminAuditTargetType.LIMITED_DROP,
				dropId, previous.name() + "->" + LimitedDropStatus.CLOSED.name());

		// 감사 로그 실패로 롤백되기 전에 Redis 키를 지우면 집계가 통째로 날아가므로 감사 로그 뒤에 flush 한다.
		limitedDropStatFlusher.flushAndClear(drop);

		return AdminLimitedDropResponse.from(drop);
	}

	public PageResponse<AdminLimitedDropSummaryResponse> getList(LimitedDropStatus status, Pageable pageable) {
		return PageResponse.from(limitedDropRepository.findAdminSummaries(status, pageable));
	}

	public AdminLimitedDropDetailResponse getDetail(Long dropId) {
		LimitedDrop drop = limitedDropRepository.findWithProductById(dropId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LIMITED_DROP_NOT_FOUND));

		Integer redisRemaining = limitedDropRedisService.getStock(dropId).orElse(null);
		List<LimitedPurchase> purchases = limitedPurchaseRepository.findAllWithMemberAndOrderByDropId(dropId);

		return AdminLimitedDropDetailResponse.from(drop, redisRemaining, purchases);
	}

	private <T> T coalesce(T newValue, T currentValue, String fieldName, List<String> changedFields) {
		if (newValue == null) {
			return currentValue;
		}
		changedFields.add(fieldName);
		return newValue;
	}
}
