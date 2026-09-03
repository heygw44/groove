package com.groove.order.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import com.groove.member.entity.MemberRole;
import com.groove.order.dto.AdminOrderDetailResponse;
import com.groove.order.dto.AdminOrderStatusChangeRequest;
import com.groove.order.dto.AdminOrderSummaryResponse;
import com.groove.order.dto.OrderItemResponse;
import com.groove.order.dto.ShippingAddressResponse;
import com.groove.order.entity.OrderStatus;
import com.groove.order.service.AdminOrderService;

@WebMvcTest(AdminOrderController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AdminOrderControllerTest {

	private static final String BASE_URL = "/api/v1/admin/orders";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	AdminOrderService adminOrderService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private AdminOrderDetailResponse sampleDetailResponse(OrderStatus status) {
		OrderItemResponse item = new OrderItemResponse(100L, "그루브 앨범", new BigDecimal("30000"), 1,
				new BigDecimal("30000"));
		ShippingAddressResponse shippingAddress = new ShippingAddressResponse("김그루브", "010-1234-5678", "06236",
				"서울시 강남구 테헤란로 1", "101동 1001호");
		return new AdminOrderDetailResponse(1L, "20260903-TESTAB12", 1L, "buyer@groove.com", status,
				new BigDecimal("90000"), BigDecimal.ZERO, new BigDecimal("90000"), null, List.of(item),
				shippingAddress, null, null, null, null);
	}

	@Nested
	@DisplayName("GET /api/v1/admin/orders")
	class GetList {

		@Test
		@DisplayName("관리자면 200 과 주문 목록을 반환한다")
		void returnsOrderListForAdmin() throws Exception {
			// given
			AdminOrderSummaryResponse summary = new AdminOrderSummaryResponse(1L, "20260903-TESTAB12",
					"buyer@groove.com", OrderStatus.PAID, new BigDecimal("90000"), 1, null);
			given(adminOrderService.getList(any())).willReturn(PageResponse.of(List.of(summary), 0, 20, 1));

			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].orderNumber", is("20260903-TESTAB12")))
					.andExpect(jsonPath("$.data.totalElements", is(1)));
		}

		@Test
		@DisplayName("from 이 to 보다 이후면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenFromAfterTo() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, adminToken())
							.param("from", "2026-09-10")
							.param("to", "2026-09-01"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminOrderService, never()).getList(any());
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminOrderService, never()).getList(any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminOrderService, never()).getList(any());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/orders/{id}")
	class GetDetail {

		@Test
		@DisplayName("관리자면 200 과 주문 상세를 반환한다")
		void returnsOrderDetailForAdmin() throws Exception {
			// given
			given(adminOrderService.getDetail(1L)).willReturn(sampleDetailResponse(OrderStatus.PAID));

			// when & then
			mockMvc.perform(get(BASE_URL + "/1").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.memberEmail", is("buyer@groove.com")))
					.andExpect(jsonPath("$.data.items[0].productName", is("그루브 앨범")))
					.andExpect(jsonPath("$.data.shippingAddress.recipientName", is("김그루브")));
		}

		@Test
		@DisplayName("존재하지 않는 주문이면 404 ORDER_NOT_FOUND 를 반환한다")
		void returnsNotFound() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND)).given(adminOrderService).getDetail(999L);

			// when & then
			mockMvc.perform(get(BASE_URL + "/999").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("ORDER_NOT_FOUND")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/1").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminOrderService, never()).getDetail(any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/1"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminOrderService, never()).getDetail(any());
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/admin/orders/{id}/status")
	class ChangeStatus {

		@Test
		@DisplayName("관리자면 200 과 변경된 주문을 반환한다")
		void changesStatusForAdmin() throws Exception {
			// given
			given(adminOrderService.changeStatus(any(), any(), any()))
					.willReturn(sampleDetailResponse(OrderStatus.PREPARING));
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.PREPARING);

			// when & then
			mockMvc.perform(patch(BASE_URL + "/1/status")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("PREPARING")));
		}

		@Test
		@DisplayName("status 를 지정하지 않으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenStatusMissing() throws Exception {
			// when & then
			mockMvc.perform(patch(BASE_URL + "/1/status")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminOrderService, never()).changeStatus(any(), any(), any());
		}

		@Test
		@DisplayName("허용되지 않는 전이면 400 ORDER_INVALID_STATUS_TRANSITION 을 반환한다")
		void returnsBadRequestWhenTransitionNotAllowed() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.ORDER_INVALID_STATUS_TRANSITION))
					.given(adminOrderService).changeStatus(any(), any(), any());
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.SHIPPED);

			// when & then
			mockMvc.perform(patch(BASE_URL + "/1/status")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("ORDER_INVALID_STATUS_TRANSITION")));
		}

		@Test
		@DisplayName("존재하지 않는 주문이면 404 ORDER_NOT_FOUND 를 반환한다")
		void returnsNotFound() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND))
					.given(adminOrderService).changeStatus(any(), any(), any());
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.PREPARING);

			// when & then
			mockMvc.perform(patch(BASE_URL + "/999/status")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("ORDER_NOT_FOUND")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenForUser() throws Exception {
			// given
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.PREPARING);

			// when & then
			mockMvc.perform(patch(BASE_URL + "/1/status")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminOrderService, never()).changeStatus(any(), any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// given
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.PREPARING);

			// when & then
			mockMvc.perform(patch(BASE_URL + "/1/status")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminOrderService, never()).changeStatus(any(), any(), any());
		}
	}
}
