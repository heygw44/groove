package com.groove.order.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.inventory.entity.Stock;
import com.groove.member.entity.MemberRole;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderCreateResponse;
import com.groove.order.dto.OrderDetailResponse;
import com.groove.order.dto.OrderSummaryResponse;
import com.groove.order.entity.OrderStatus;
import com.groove.order.service.OrderService;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class OrderControllerTest {

	private static final String BASE_URL = "/api/v1/orders";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	OrderService orderService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private OrderCreateResponse sampleResponse() {
		return new OrderCreateResponse(1L, "20260903-TESTAB12", new BigDecimal("90000"), BigDecimal.ZERO,
				new BigDecimal("90000"), null);
	}

	private OrderDetailResponse sampleDetailResponse(OrderStatus status) {
		return sampleDetailResponse(status, null);
	}

	private OrderDetailResponse sampleDetailResponse(OrderStatus status, Long limitedDropId) {
		return new OrderDetailResponse(1L, "20260903-TESTAB12", status, new BigDecimal("90000"), BigDecimal.ZERO,
				new BigDecimal("90000"), null, List.of(), null, null, null, null, null, limitedDropId, null);
	}

	@Nested
	@DisplayName("POST /api/v1/orders")
	class Create {

		@Test
		@DisplayName("유효한 요청이면 201 과 생성된 주문을 반환한다")
		void createsOrder() throws Exception {
			// given
			given(orderService.create(eq(1L), any())).willReturn(sampleResponse());
			OrderCreateRequest request = new OrderCreateRequest(null, 100L, 2, 10L, null);

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.orderId", is(1)))
					.andExpect(jsonPath("$.data.orderNumber", is("20260903-TESTAB12")));
			verify(orderService).create(eq(1L), any());
		}

		@Test
		@DisplayName("재고 락 대기에 실패하면 409 STOCK_CONFLICT 를 반환한다")
		void returnsConflictWhenStockLockFails() throws Exception {
			// given: 재고 락 실패는 OrderStockService 가 도메인 코드로 확정해 던진다.
			willThrow(new BusinessException(ErrorCode.STOCK_CONFLICT))
					.given(orderService).create(eq(1L), any());
			OrderCreateRequest request = new OrderCreateRequest(null, 100L, 1, 10L, null);

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("STOCK_CONFLICT")));
		}

		@Test
		@DisplayName("재고 버전 충돌이 나면 409 STOCK_CONFLICT 를 반환한다")
		void returnsConflictWhenStockVersionConflicts() throws Exception {
			// given
			willThrow(new ObjectOptimisticLockingFailureException(Stock.class.getName(), 1L))
					.given(orderService).create(eq(1L), any());
			OrderCreateRequest request = new OrderCreateRequest(null, 100L, 1, 10L, null);

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("STOCK_CONFLICT")));
		}

		@Test
		@DisplayName("재고가 아닌 락 대기 실패는 409 COMMON_CONFLICT 를 반환한다")
		void returnsCommonConflictWhenCouponLockFails() throws Exception {
			// given: 쿠폰 락 실패는 서비스가 변환하지 않아 전역 핸들러까지 올라온다.
			willThrow(new PessimisticLockingFailureException("lock wait timeout"))
					.given(orderService).create(eq(1L), any());
			OrderCreateRequest request = new OrderCreateRequest(null, 100L, 1, 10L, null);

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("COMMON_CONFLICT")));
		}

		@Test
		@DisplayName("cartItemIds 와 productId+quantity 를 둘 다 지정하면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenBothSourcesSpecified() throws Exception {
			// given
			OrderCreateRequest request = new OrderCreateRequest(List.of(1L), 100L, 2, 10L, null);

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(orderService, never()).create(any(), any());
		}

		@Test
		@DisplayName("cartItemIds 와 productId+quantity 를 둘 다 지정하지 않으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenNoSourceSpecified() throws Exception {
			// given
			OrderCreateRequest request = new OrderCreateRequest(null, null, null, 10L, null);

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(orderService, never()).create(any(), any());
		}

		@Test
		@DisplayName("memberCouponId 를 지정하면 201 과 할인이 반영된 주문을 반환한다")
		void createsOrderWithCoupon() throws Exception {
			// given
			OrderCreateResponse response = new OrderCreateResponse(1L, "20260903-TESTAB12",
					new BigDecimal("90000"), new BigDecimal("5000"), new BigDecimal("85000"), "가을맞이 할인");
			given(orderService.create(eq(1L), any())).willReturn(response);
			OrderCreateRequest request = new OrderCreateRequest(null, 100L, 2, 10L, 5L);

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.discountAmount", is(5000)))
					.andExpect(jsonPath("$.data.couponName", is("가을맞이 할인")));
			verify(orderService).create(eq(1L), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// given
			OrderCreateRequest request = new OrderCreateRequest(null, 100L, 2, 10L, null);

			// when & then
			mockMvc.perform(post(BASE_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(orderService, never()).create(any(), any());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/orders")
	class GetMyOrders {

		@Test
		@DisplayName("유효한 요청이면 200 과 주문 목록을 반환한다")
		void returnsOrderList() throws Exception {
			// given
			OrderSummaryResponse summary = new OrderSummaryResponse(1L, "20260903-TESTAB12", OrderStatus.PENDING,
					new BigDecimal("90000"), BigDecimal.ZERO, null, "Kind of Blue", 1, null, null);
			given(orderService.getMyOrders(eq(1L), any())).willReturn(PageResponse.of(List.of(summary), 0, 20, 1));

			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].orderNumber", is("20260903-TESTAB12")))
					.andExpect(jsonPath("$.data.totalElements", is(1)));
		}

		@Test
		@DisplayName("size 가 100 을 넘으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenSizeTooLarge() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, bearer()).param("size", "101"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(orderService, never()).getMyOrders(any(), any());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/orders/{id}")
	class GetDetail {

		@Test
		@DisplayName("유효한 요청이면 200 과 주문 상세를 반환한다")
		void returnsOrderDetail() throws Exception {
			// given
			given(orderService.getDetail(eq(1L), eq(1L)))
					.willReturn(sampleDetailResponse(OrderStatus.PENDING, 10L));

			// when & then
			mockMvc.perform(get(BASE_URL + "/1").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.orderNumber", is("20260903-TESTAB12")))
					.andExpect(jsonPath("$.data.limitedDropId", is(10)));
		}

		@Test
		@DisplayName("존재하지 않거나 타인 주문이면 404 ORDER_NOT_FOUND 를 반환한다")
		void returnsNotFound() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND))
					.given(orderService).getDetail(eq(1L), eq(999L));

			// when & then
			mockMvc.perform(get(BASE_URL + "/999").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("ORDER_NOT_FOUND")));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/orders/{id}/cancel")
	class Cancel {

		@Test
		@DisplayName("바디 없이 요청해도 200 과 취소된 주문을 반환한다")
		void cancelsWithoutBody() throws Exception {
			// given
			given(orderService.cancel(eq(1L), eq(1L), eq(null)))
					.willReturn(sampleDetailResponse(OrderStatus.CANCELED));

			// when & then
			mockMvc.perform(post(BASE_URL + "/1/cancel").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCELED")));
		}
	}
}
