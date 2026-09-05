package com.groove.payment.controller;

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
import com.groove.fixture.PaymentFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.payment.dto.PaymentCancelRequest;
import com.groove.payment.dto.PaymentCancelResponse;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.dto.PaymentConfirmResponse;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.service.PaymentConfirmService;
import com.groove.payment.service.PaymentService;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class PaymentControllerTest {

	private static final String BASE_URL = "/api/v1/payments";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	PaymentConfirmService paymentConfirmService;

	@MockitoBean
	PaymentService paymentService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private PaymentConfirmResponse sampleResponse() {
		return new PaymentConfirmResponse(3001L, 9002L, "20260902-K7Q2M9XZ", PaymentStatus.DONE,
				PaymentFixture.METHOD, new BigDecimal("75600"), PaymentFixture.APPROVED_AT);
	}

	@Nested
	@DisplayName("POST /api/v1/payments/confirm")
	class Confirm {

		@Test
		@DisplayName("유효한 요청이면 200 과 승인 결과를 반환한다")
		void confirmsPayment() throws Exception {
			// given
			given(paymentConfirmService.confirm(eq(1L), any())).willReturn(sampleResponse());
			PaymentConfirmRequest request = new PaymentConfirmRequest("tviva20260902abcdef", "20260902-K7Q2M9XZ",
					75600L);

			// when & then
			mockMvc.perform(post(BASE_URL + "/confirm")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.paymentId", is(3001)))
					.andExpect(jsonPath("$.data.status", is("DONE")));
			verify(paymentConfirmService).confirm(eq(1L), any());
		}

		@Test
		@DisplayName("금액이 일치하지 않으면 400 ORDER_AMOUNT_MISMATCH 를 반환한다")
		void returnsBadRequestWhenAmountMismatch() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.ORDER_AMOUNT_MISMATCH))
					.given(paymentConfirmService).confirm(eq(1L), any());
			PaymentConfirmRequest request = new PaymentConfirmRequest("tviva20260902abcdef", "20260902-K7Q2M9XZ",
					1L);

			// when & then
			mockMvc.perform(post(BASE_URL + "/confirm")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("ORDER_AMOUNT_MISMATCH")));
		}

		@Test
		@DisplayName("필수 값이 비어 있으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenValidationFails() throws Exception {
			// given
			PaymentConfirmRequest request = new PaymentConfirmRequest("", "20260902-K7Q2M9XZ", 75600L);

			// when & then
			mockMvc.perform(post(BASE_URL + "/confirm")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(paymentConfirmService, never()).confirm(any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// given
			PaymentConfirmRequest request = new PaymentConfirmRequest("tviva20260902abcdef", "20260902-K7Q2M9XZ",
					75600L);

			// when & then
			mockMvc.perform(post(BASE_URL + "/confirm")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(paymentConfirmService, never()).confirm(any(), any());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/payments/{id}/cancel")
	class Cancel {

		@Test
		@DisplayName("유효한 요청이면 200 과 취소 결과를 반환한다")
		void cancelsPayment() throws Exception {
			// given
			PaymentCancelResponse response = new PaymentCancelResponse(3001L, 9002L, "20260902-K7Q2M9XZ",
					PaymentStatus.CANCELED, PaymentFixture.CANCELED_AT);
			given(paymentService.cancel(eq(1L), eq(3001L), any())).willReturn(response);
			PaymentCancelRequest request = new PaymentCancelRequest("고객 변심");

			// when & then
			mockMvc.perform(post(BASE_URL + "/3001/cancel")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.paymentId", is(3001)))
					.andExpect(jsonPath("$.data.status", is("CANCELED")));
			verify(paymentService).cancel(eq(1L), eq(3001L), any());
		}

		@Test
		@DisplayName("취소 사유가 비어 있으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenReasonIsBlank() throws Exception {
			// given
			PaymentCancelRequest request = new PaymentCancelRequest("");

			// when & then
			mockMvc.perform(post(BASE_URL + "/3001/cancel")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(paymentService, never()).cancel(any(), any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// given
			PaymentCancelRequest request = new PaymentCancelRequest("고객 변심");

			// when & then
			mockMvc.perform(post(BASE_URL + "/3001/cancel")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(paymentService, never()).cancel(any(), any(), any());
		}
	}
}
