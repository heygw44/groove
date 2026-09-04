package com.groove.inventory.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.inventory.dto.StockAdjustRequest;
import com.groove.inventory.dto.StockResponse;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.service.StockService;
import com.groove.member.entity.MemberRole;
import com.groove.product.entity.ProductStatus;

@WebMvcTest(AdminStockController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AdminStockControllerTest {

	private static final Long PRODUCT_ID = 1L;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	StockService stockService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	@Nested
	@DisplayName("PATCH /api/v1/admin/products/{productId}/stock")
	class Adjust {

		@Test
		@DisplayName("관리자면 200 과 조정된 재고 정보를 반환한다")
		void adjustsStockWhenAdmin() throws Exception {
			// given
			StockAdjustRequest request = StockFixture.adjustRequest(StockChangeType.IN, 5);
			StockResponse response = new StockResponse(PRODUCT_ID, 15, ProductStatus.ON_SALE);
			given(stockService.adjust(eq(PRODUCT_ID), any())).willReturn(response);

			// when & then
			mockMvc.perform(patch("/api/v1/admin/products/{productId}/stock", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.quantity", is(15)));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// given
			StockAdjustRequest request = StockFixture.adjustRequest(StockChangeType.IN, 5);

			// when & then
			mockMvc.perform(patch("/api/v1/admin/products/{productId}/stock", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(stockService, never()).adjust(any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// given
			StockAdjustRequest request = StockFixture.adjustRequest(StockChangeType.IN, 5);

			// when & then
			mockMvc.perform(patch("/api/v1/admin/products/{productId}/stock", PRODUCT_ID)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(stockService, never()).adjust(any(), any());
		}

		@ParameterizedTest
		@DisplayName("수량이 0 이하면 400 과 필드 에러를 반환한다")
		@ValueSource(ints = {0, -1})
		void returnsBadRequestWhenQuantityNotPositive(int quantity) throws Exception {
			// given
			StockAdjustRequest request = new StockAdjustRequest(StockChangeType.IN, quantity, "사유");

			// when & then
			mockMvc.perform(patch("/api/v1/admin/products/{productId}/stock", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("quantity")));
		}

		@Test
		@DisplayName("낙관적 락 충돌이면 409 STOCK_CONFLICT 를 반환한다")
		void returnsConflictWhenOptimisticLockFails() throws Exception {
			// given
			StockAdjustRequest request = StockFixture.adjustRequest(StockChangeType.OUT, 1);
			willThrow(new ObjectOptimisticLockingFailureException(Stock.class.getName(), 1L))
					.given(stockService).adjust(eq(PRODUCT_ID), any());

			// when & then
			mockMvc.perform(patch("/api/v1/admin/products/{productId}/stock", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("STOCK_CONFLICT")));
		}

		@Test
		@DisplayName("재고가 부족하면 409 STOCK_INSUFFICIENT 를 반환한다")
		void returnsConflictWhenInsufficient() throws Exception {
			// given
			StockAdjustRequest request = StockFixture.adjustRequest(StockChangeType.OUT, 100);
			willThrow(new BusinessException(ErrorCode.STOCK_INSUFFICIENT))
					.given(stockService).adjust(eq(PRODUCT_ID), any());

			// when & then
			mockMvc.perform(patch("/api/v1/admin/products/{productId}/stock", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("STOCK_INSUFFICIENT")));
		}
	}
}
