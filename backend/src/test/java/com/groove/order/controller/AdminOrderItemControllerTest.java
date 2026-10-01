package com.groove.order.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
import com.groove.global.common.SliceResponse;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.order.dto.AdminOrderItemBulkResultResponse;
import com.groove.order.dto.AdminOrderItemConfirmRequest;
import com.groove.order.dto.AdminOrderItemCountResponse;
import com.groove.order.dto.AdminOrderItemDeliverRequest;
import com.groove.order.dto.AdminOrderItemShipRequest;
import com.groove.order.dto.AdminOrderItemSummaryResponse;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.service.AdminOrderItemService;

@WebMvcTest(AdminOrderItemController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AdminOrderItemControllerTest {

	private static final String BASE_URL = "/api/v1/admin/order-items";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	AdminOrderItemService adminOrderItemService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	@Nested
	@DisplayName("GET /api/v1/admin/order-items")
	class GetList {

		@Test
		@DisplayName("관리자면 200 과 상품주문 목록을 반환한다")
		void returnsListForAdmin() throws Exception {
			// given
			AdminOrderItemSummaryResponse summary = new AdminOrderItemSummaryResponse(900L, 700L,
					"20260903-TESTAB12-01", "20260903-TESTAB12", "buyer@groove.com", "그루브 앨범", 1,
					OrderItemStatus.PAID, null, null, null, LocalDateTime.now(), false);
			given(adminOrderItemService.getList(any())).willReturn(SliceResponse.of(List.of(summary), 0, 20));

			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].productOrderNumber", is("20260903-TESTAB12-01")))
					.andExpect(jsonPath("$.data.content[0].orderId", is(700)))
					.andExpect(jsonPath("$.data.hasNext", is(false)))
					.andExpect(jsonPath("$.data.totalElements").doesNotExist());
		}

		@Test
		@DisplayName("from 이 to 보다 이후면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenPeriodInverted() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL).param("from", "2026-09-10").param("to", "2026-09-01")
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminOrderItemService, never()).getList(any());
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환한다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminOrderItemService, never()).getList(any());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/order-items/count")
	class Count {

		@Test
		@DisplayName("관리자면 200 과 totalElements 를 반환한다")
		void returnsCountForAdmin() throws Exception {
			// given
			given(adminOrderItemService.count(any())).willReturn(new AdminOrderItemCountResponse(37L));

			// when & then
			mockMvc.perform(get(BASE_URL + "/count").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.totalElements", is(37)));
		}

		@Test
		@DisplayName("from 이 to 보다 이후면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenPeriodInverted() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/count").param("from", "2026-09-10").param("to", "2026-09-01")
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminOrderItemService, never()).count(any());
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환한다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/count").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminOrderItemService, never()).count(any());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/admin/order-items/confirm")
	class ConfirmPreparing {

		@Test
		@DisplayName("관리자면 200 과 처리 결과를 반환한다")
		void confirmsForAdmin() throws Exception {
			// given
			given(adminOrderItemService.confirmPreparing(eq(1L), any()))
					.willReturn(new AdminOrderItemBulkResultResponse(2, 0));

			// when & then
			AdminOrderItemConfirmRequest request = new AdminOrderItemConfirmRequest(List.of(1L, 2L));
			mockMvc.perform(post(BASE_URL + "/confirm").header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.processed", is(2)))
					.andExpect(jsonPath("$.data.skipped", is(0)));
		}

		@Test
		@DisplayName("빈 배열이면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenEmpty() throws Exception {
			// when & then
			mockMvc.perform(post(BASE_URL + "/confirm").header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new AdminOrderItemConfirmRequest(List.of()))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminOrderItemService, never()).confirmPreparing(any(), any());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/admin/order-items/ship")
	class StartShipping {

		@Test
		@DisplayName("관리자면 200 과 처리 결과를 반환한다")
		void shipsForAdmin() throws Exception {
			// given
			given(adminOrderItemService.startShipping(eq(1L), any()))
					.willReturn(new AdminOrderItemBulkResultResponse(1, 0));
			AdminOrderItemShipRequest request = shipRequest(shipItem(1L, "CJ", "123456789012"));

			// when & then
			mockMvc.perform(post(BASE_URL + "/ship").header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.processed", is(1)));
		}

		@Test
		@DisplayName("항목마다 다른 택배사·송장으로 요청할 수 있다")
		void shipsWithPerItemCourierAndTracking() throws Exception {
			// given
			given(adminOrderItemService.startShipping(eq(1L), any()))
					.willReturn(new AdminOrderItemBulkResultResponse(2, 0));
			AdminOrderItemShipRequest request = shipRequest(shipItem(1L, "CJ", "111111111111"),
					shipItem(2L, "HANJIN", "222222222222"));

			// when & then
			mockMvc.perform(post(BASE_URL + "/ship").header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.processed", is(2)));
		}

		@Test
		@DisplayName("송장번호가 없으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenTrackingNumberBlank() throws Exception {
			// given
			AdminOrderItemShipRequest request = shipRequest(shipItem(1L, "CJ", ""));

			// when & then
			mockMvc.perform(post(BASE_URL + "/ship").header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminOrderItemService, never()).startShipping(any(), any());
		}

		@Test
		@DisplayName("같은 orderItemId 가 중복되면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenOrderItemIdDuplicated() throws Exception {
			// given
			AdminOrderItemShipRequest request = shipRequest(shipItem(1L, "CJ", "111111111111"),
					shipItem(1L, "HANJIN", "222222222222"));

			// when & then
			mockMvc.perform(post(BASE_URL + "/ship").header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminOrderItemService, never()).startShipping(any(), any());
		}

		@Test
		@DisplayName("items 가 비어 있으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenItemsEmpty() throws Exception {
			// when & then
			mockMvc.perform(post(BASE_URL + "/ship").header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new AdminOrderItemShipRequest(List.of()))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminOrderItemService, never()).startShipping(any(), any());
		}

		private AdminOrderItemShipRequest.ShipItem shipItem(Long orderItemId, String courierCode,
				String trackingNumber) {
			return new AdminOrderItemShipRequest.ShipItem(orderItemId, courierCode, trackingNumber);
		}

		private AdminOrderItemShipRequest shipRequest(AdminOrderItemShipRequest.ShipItem... items) {
			return new AdminOrderItemShipRequest(List.of(items));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/admin/order-items/deliver")
	class CompleteDelivery {

		@Test
		@DisplayName("관리자면 200 과 처리 결과를 반환한다")
		void deliversForAdmin() throws Exception {
			// given
			given(adminOrderItemService.completeDelivery(eq(1L), any()))
					.willReturn(new AdminOrderItemBulkResultResponse(1, 0));

			// when & then
			mockMvc.perform(post(BASE_URL + "/deliver").header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new AdminOrderItemDeliverRequest(List.of(1L)))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.processed", is(1)));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(post(BASE_URL + "/deliver")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new AdminOrderItemDeliverRequest(List.of(1L)))))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminOrderItemService, never()).completeDelivery(any(), any());
		}
	}
}
