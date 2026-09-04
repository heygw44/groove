package com.groove.payment.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.client.dto.TossCancelRequest;
import com.groove.payment.client.dto.TossConfirmRequest;
import com.groove.payment.client.dto.TossErrorResponse;
import com.groove.payment.client.dto.TossPaymentResponse;
import com.groove.payment.config.TossProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 토스 페이먼츠 연동 구현. 토스가 돌려준 에러 코드와 메시지는 로그와 예외 상세에만 싣고
 * 사용자 응답에는 ErrorCode 의 고정 메시지만 나간다.
 */
@Slf4j
@Component
public class TossPaymentClient implements PaymentClient {

	private static final String CONFIRM_PATH = "/v1/payments/confirm";
	private static final String CANCEL_PATH = "/v1/payments/{paymentKey}/cancel";
	private static final String UNKNOWN_ERROR_CODE = "UNKNOWN";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final String authorization;

	public TossPaymentClient(RestClient tossRestClient, ObjectMapper objectMapper, Clock clock,
			TossProperties properties) {
		this.restClient = tossRestClient;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.authorization = basicAuthorization(properties.secretKey());
	}

	@Override
	public PaymentConfirmResult confirm(String paymentKey, String orderId, BigDecimal amount) {
		TossConfirmRequest request = new TossConfirmRequest(paymentKey, orderId, toWon(amount));
		TossPaymentResponse response = send(ErrorCode.PAYMENT_CONFIRM_FAILED, paymentKey, CONFIRM_PATH, request);
		return new PaymentConfirmResult(response.paymentKey(), response.orderId(), response.method(),
				response.totalAmount(), toServerTime(response.approvedAt()));
	}

	@Override
	public PaymentCancelResult cancel(String paymentKey, String reason) {
		TossCancelRequest request = new TossCancelRequest(reason);
		TossPaymentResponse response = send(ErrorCode.PAYMENT_CANCEL_FAILED, paymentKey, CANCEL_PATH, request,
				paymentKey);
		TossPaymentResponse.Cancel lastCancel = response.lastCancel();
		LocalDateTime canceledAt = lastCancel == null ? null : toServerTime(lastCancel.canceledAt());
		return new PaymentCancelResult(response.paymentKey(), response.status(), canceledAt);
	}

	private TossPaymentResponse send(ErrorCode errorCode, String paymentKey, String uri, Object body,
			Object... uriVariables) {
		try {
			TossPaymentResponse response = restClient.post()
					.uri(uri, uriVariables)
					.header(HttpHeaders.AUTHORIZATION, authorization)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(TossPaymentResponse.class);
			if (response == null) {
				throw new BusinessException(errorCode, "TOSS 응답 본문이 비어 있습니다.");
			}
			return response;
		} catch (RestClientResponseException ex) {
			TossErrorResponse error = parseError(ex.getResponseBodyAsString());
			log.warn("토스 결제 API 오류 응답: errorCode={}, paymentKey={}, tossCode={}, tossMessage={}",
					errorCode.name(), paymentKey, error.code(), error.message());
			throw new BusinessException(errorCode, "TOSS " + error.code() + ": " + error.message());
		} catch (RestClientException ex) {
			log.warn("토스 결제 API 통신 실패: errorCode={}, paymentKey={}", errorCode.name(), paymentKey, ex);
			throw new BusinessException(errorCode, "TOSS 통신 실패: " + ex.getMessage());
		}
	}

	/** 토스 Basic 인증은 시크릿 키를 사용자 ID 로 쓰고 비밀번호가 없어 콜론만 덧붙인다. */
	private String basicAuthorization(String secretKey) {
		String credentials = secretKey + ":";
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}

	/** 토스는 금액을 원 단위 정수로 받는다. DECIMAL(10,2) 로 저장된 주문 금액을 그대로 보내면 소수점이 따라간다. */
	private long toWon(BigDecimal amount) {
		return amount.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
	}

	private LocalDateTime toServerTime(OffsetDateTime time) {
		if (time == null) {
			return null;
		}
		return time.atZoneSameInstant(clock.getZone()).toLocalDateTime();
	}

	private TossErrorResponse parseError(String body) {
		if (body == null || body.isBlank()) {
			return new TossErrorResponse(UNKNOWN_ERROR_CODE, "");
		}
		try {
			TossErrorResponse error = objectMapper.readValue(body, TossErrorResponse.class);
			return error.code() == null ? new TossErrorResponse(UNKNOWN_ERROR_CODE, body) : error;
		} catch (JsonProcessingException ex) {
			return new TossErrorResponse(UNKNOWN_ERROR_CODE, body);
		}
	}
}
