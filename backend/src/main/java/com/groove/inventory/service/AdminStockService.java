package com.groove.inventory.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.inventory.dto.StockAdjustRequest;
import com.groove.inventory.dto.StockResponse;
import com.groove.inventory.entity.StockChangeType;

import lombok.RequiredArgsConstructor;

/** 관리자 재고 조정 + 감사 로그 기록. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminStockService {

	private static final Map<StockChangeType, String> CHANGE_TYPE_LABELS = Map.of(
			StockChangeType.IN, "입고",
			StockChangeType.OUT, "출고",
			StockChangeType.ADJUST, "조정",
			StockChangeType.CANCEL, "취소");

	private final StockService stockService;
	private final AdminAuditLogService adminAuditLogService;

	@Transactional
	public StockResponse adjust(Long adminId, Long productId, StockAdjustRequest request) {
		int before = stockService.getByProductId(productId).quantity();
		StockResponse response = stockService.adjust(productId, request);
		String detail = CHANGE_TYPE_LABELS.get(request.changeType()) + " " + before + " → " + response.quantity();
		adminAuditLogService.record(adminId, AdminAuditAction.STOCK_ADJUST, AdminAuditTargetType.PRODUCT, productId,
				detail);
		return response;
	}
}
