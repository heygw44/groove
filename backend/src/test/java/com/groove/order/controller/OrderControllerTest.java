package com.groove.order.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderCreateResponse;
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
		return new OrderCreateResponse(1L, "20260903-TESTAB12", new BigDecimal("90000"));
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
			// given
			willThrow(new PessimisticLockingFailureException("lock wait timeout"))
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
		@DisplayName("memberCouponId 를 지정하면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenMemberCouponIdSpecified() throws Exception {
			// given
			OrderCreateRequest request = new OrderCreateRequest(null, 100L, 2, 10L, 5L);

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("couponNotSupported")));
			verify(orderService, never()).create(any(), any());
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
}
