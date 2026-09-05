package com.groove.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.inventory.dto.StockAdjustRequest;
import com.groove.inventory.dto.StockResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 재고 조정 + 감사 로그 기록. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminStockService {

	private final StockService stockService;
	private final AdminAuditLogService adminAuditLogService;

	@Transactional
	public StockResponse adjust(Long adminId, Long productId, StockAdjustRequest request) {
		int before = stockService.getByProductId(productId).quantity();
		StockResponse response = stockService.adjust(productId, request);
		String detail = request.changeType().name() + ":" + before + "->" + response.quantity();
		adminAuditLogService.record(adminId, AdminAuditAction.STOCK_ADJUST, AdminAuditTargetType.PRODUCT, productId,
				detail);
		return response;
	}
}
