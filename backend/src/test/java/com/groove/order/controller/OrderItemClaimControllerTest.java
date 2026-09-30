package com.groove.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.order.dto.OrderItemResponse;
import com.groove.order.dto.OrderReturnRequest;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.service.OrderItemClaimService;

@WebMvcTest(OrderItemClaimController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class OrderItemClaimControllerTest {

	private static final String RETURN_URL = "/api/v1/orders/10/items/100/return";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	OrderItemClaimService orderItemClaimService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private OrderItemResponse sampleResponse() {
		return new OrderItemResponse(100L, 620L, "Head Hunters", new BigDecimal("6000"), 1,
				new BigDecimal("6000"), null, "20260902-K7Q2M9XZ-01", OrderItemStatus.DELIVERED,
				OrderItemClaimStatus.RETURN_REQUEST, new BigDecimal("6000"), null, null, null, List.of(), 900L,
				false);
	}

	@Nested
	@DisplayName("POST /api/v1/orders/{orderId}/items/{itemId}/return")
	class ReturnItem {

		@Test
		@DisplayName("환불계좌가 담긴 본문이면 200 을 반환하고 서비스에 계좌를 그대로 전달한다")
		void acceptsRefundAccount() throws Exception {
			// given
			given(orderItemClaimService.returnItem(eq(1L), eq(10L), eq(100L), any(OrderReturnRequest.class)))
					.willReturn(sampleResponse());
			String body = "{\"reason\": \"단순 변심\", \"refundAccount\": {\"bankCode\": \"088\", "
					+ "\"accountNumber\": \"110123456789\", \"holderName\": \"홍길동\"}}";

			// when & then
			mockMvc.perform(post(RETURN_URL).header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.claimId", is(900)));
			ArgumentCaptor<OrderReturnRequest> captor = ArgumentCaptor.forClass(OrderReturnRequest.class);
			verify(orderItemClaimService).returnItem(eq(1L), eq(10L), eq(100L), captor.capture());
			assertThat(captor.getValue().reason()).isEqualTo("단순 변심");
			assertThat(captor.getValue().refundAccount().bankCode()).isEqualTo("088");
			assertThat(captor.getValue().refundAccount().accountNumber()).isEqualTo("110123456789");
			assertThat(captor.getValue().refundAccount().holderName()).isEqualTo("홍길동");
		}

		@Test
		@DisplayName("환불계좌 없이 사유만 보내도 200 을 반환한다")
		void acceptsBodyWithoutRefundAccount() throws Exception {
			// given
			given(orderItemClaimService.returnItem(eq(1L), eq(10L), eq(100L), any(OrderReturnRequest.class)))
					.willReturn(sampleResponse());

			// when & then
			mockMvc.perform(post(RETURN_URL).header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"단순 변심\"}"))
					.andExpect(status().isOk());
			ArgumentCaptor<OrderReturnRequest> captor = ArgumentCaptor.forClass(OrderReturnRequest.class);
			verify(orderItemClaimService).returnItem(eq(1L), eq(10L), eq(100L), captor.capture());
			assertThat(captor.getValue().refundAccount()).isNull();
		}

		@Test
		@DisplayName("환불계좌의 필수 항목이 비어 있으면 400 을 반환하고 서비스를 부르지 않는다")
		void returnsBadRequestWhenRefundAccountFieldBlank() throws Exception {
			// given
			String body = "{\"reason\": \"단순 변심\", \"refundAccount\": {\"bankCode\": \"\", "
					+ "\"accountNumber\": \"110123456789\", \"holderName\": \"홍길동\"}}";

			// when & then
			mockMvc.perform(post(RETURN_URL).header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
			verify(orderItemClaimService, never()).returnItem(any(), any(), any(), any());
		}

		@Test
		@DisplayName("서비스가 PAYMENT_REFUND_ACCOUNT_REQUIRED 를 던지면 400 으로 응답한다")
		void returnsBadRequestWhenAccountRequired() throws Exception {
			// given
			given(orderItemClaimService.returnItem(eq(1L), eq(10L), eq(100L), any()))
					.willThrow(new BusinessException(ErrorCode.PAYMENT_REFUND_ACCOUNT_REQUIRED));

			// when & then
			mockMvc.perform(post(RETURN_URL).header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"단순 변심\"}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("PAYMENT_REFUND_ACCOUNT_REQUIRED")));
		}
	}
}
