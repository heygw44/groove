package com.groove.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.inventory.dto.StockAdjustRequest;
import com.groove.inventory.dto.StockResponse;
import com.groove.inventory.entity.StockChangeType;
import com.groove.product.entity.ProductStatus;

@ExtendWith(MockitoExtension.class)
class AdminStockServiceTest {

	private static final Long ADMIN_ID = 1L;
	private static final Long PRODUCT_ID = 10L;

	@Mock
	StockService stockService;

	@Mock
	AdminAuditLogService adminAuditLogService;

	AdminStockService adminStockService;

	@BeforeEach
	void setUp() {
		adminStockService = new AdminStockService(stockService, adminAuditLogService);
	}

	@Nested
	@DisplayName("adjust()")
	class Adjust {

		@Test
		@DisplayName("조정 전후 수량을 detail 에 담아 STOCK_ADJUST 감사 로그를 남긴다")
		void recordsStockAdjustAuditLog() {
			// given
			StockAdjustRequest request = new StockAdjustRequest(StockChangeType.IN, 5, "입고");
			given(stockService.getByProductId(PRODUCT_ID))
					.willReturn(new StockResponse(PRODUCT_ID, 10, ProductStatus.ON_SALE));
			given(stockService.adjust(PRODUCT_ID, request))
					.willReturn(new StockResponse(PRODUCT_ID, 15, ProductStatus.ON_SALE));

			// when
			StockResponse response = adminStockService.adjust(ADMIN_ID, PRODUCT_ID, request);

			// then
			assertThat(response.quantity()).isEqualTo(15);
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.STOCK_ADJUST),
					eq(AdminAuditTargetType.PRODUCT), eq(PRODUCT_ID), eq("IN:10->15"));
		}

		@Test
		@DisplayName("재고 조정을 먼저 수행한 뒤 감사 로그를 남긴다")
		void adjustsStockBeforeRecording() {
			// given
			StockAdjustRequest request = new StockAdjustRequest(StockChangeType.OUT, 3, "출고");
			given(stockService.getByProductId(PRODUCT_ID))
					.willReturn(new StockResponse(PRODUCT_ID, 10, ProductStatus.ON_SALE));
			given(stockService.adjust(PRODUCT_ID, request))
					.willReturn(new StockResponse(PRODUCT_ID, 7, ProductStatus.ON_SALE));

			// when
			adminStockService.adjust(ADMIN_ID, PRODUCT_ID, request);

			// then
			verify(stockService).adjust(eq(PRODUCT_ID), any());
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.STOCK_ADJUST),
					eq(AdminAuditTargetType.PRODUCT), eq(PRODUCT_ID), eq("OUT:10->7"));
		}
	}
}
