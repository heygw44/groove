package com.groove.payment.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import com.groove.payment.service.PaymentWebhookService;

@WebMvcTest(PaymentWebhookController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class PaymentWebhookControllerTest {

	private static final String URL = "/api/v1/payments/webhook";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PaymentWebhookService paymentWebhookService;

	@Nested
	@DisplayName("POST /api/v1/payments/webhook")
	class Webhook {

		@Test
		@DisplayName("인증 토큰 없이 호출해도 200 을 반환한다")
		void acceptsWithoutAuthentication() throws Exception {
			// given
			String body = "{ \"eventType\": \"PAYMENT_STATUS_CHANGED\", \"createdAt\": "
					+ "\"2026-09-22T10:00:00+09:00\", \"data\": { \"paymentKey\": \"tviva-key\", "
					+ "\"orderId\": \"20260922-ABCDEFGH\", \"status\": \"DONE\" } }";

			// when & then
			mockMvc.perform(post(URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isOk());
			verify(paymentWebhookService).handle(body);
		}

		@Test
		@DisplayName("대상 이벤트가 아니어도 서비스에 그대로 넘기고 200 을 반환한다")
		void passesThroughUnknownEventType() throws Exception {
			// given
			String body = "{ \"eventType\": \"METHOD_UPDATED\", \"createdAt\": \"2026-09-22T10:00:00+09:00\", "
					+ "\"data\": {} }";

			// when & then
			mockMvc.perform(post(URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isOk());
			verify(paymentWebhookService).handle(body);
		}

		@Test
		@DisplayName("본문이 JSON 형식이 아니어도 200 을 반환한다")
		void returnsOkForBrokenBody() throws Exception {
			// given
			String body = "이건 JSON 이 아니다 {{{";

			// when & then
			mockMvc.perform(post(URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isOk());
			verify(paymentWebhookService).handle(body);
		}

		@Test
		@DisplayName("서비스가 예외를 던지면(DB 장애) 500 을 반환한다")
		void returnsInternalServerErrorWhenServiceFails() throws Exception {
			// given
			willThrow(new RuntimeException("DB 장애")).given(paymentWebhookService).handle(any());
			String body = "{}";

			// when & then
			mockMvc.perform(post(URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isInternalServerError());
		}

		@Test
		@DisplayName("재조회·적용 실패로 서비스가 PAYMENT_RESULT_UNKNOWN 을 던지면 503 을 반환한다")
		void returnsServiceUnavailableWhenLookupResultIsUnknown() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, "TOSS 통신 실패"))
					.given(paymentWebhookService).handle(any());
			String body = "{}";

			// when & then
			mockMvc.perform(post(URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.error.code", is("PAYMENT_RESULT_UNKNOWN")));
		}
	}
}
