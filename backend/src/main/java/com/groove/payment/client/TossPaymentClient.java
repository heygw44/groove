package com.groove.payment.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
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
	private static final String LOOKUP_PATH = "/v1/payments/orders/{orderId}";
	private static final String UNKNOWN_ERROR_CODE = "UNKNOWN";

	/**
	 * 토스는 멱등키+API 키+URL+메서드로 요청을 판별하고 본문은 보지 않아 첫 응답을 15일간 그대로 재생한다.
	 * 승인 키를 orderId 로 잡으면 카드 거절 뒤 다른 카드(새 paymentKey)로 재결제할 때 첫 거절이 재생되므로
	 * 결제 시도 단위인 paymentKey 로 잡는다.
	 */
	private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
	private static final String CONFIRM_IDEMPOTENCY_PREFIX = "confirm-";
	private static final String CANCEL_IDEMPOTENCY_PREFIX = "cancel-";

	/** 조회 대상 결제가 없다는 토스 에러 코드. */
	private static final Set<String> NOT_FOUND_ERROR_CODES = Set.of("NOT_FOUND_PAYMENT", "NOT_FOUND");

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
		String idempotencyKey = CONFIRM_IDEMPOTENCY_PREFIX + paymentKey;
		TossPaymentResponse response;
		try {
			response = send(ErrorCode.PAYMENT_CONFIRM_FAILED, paymentKey, idempotencyKey, CONFIRM_PATH, request);
		} catch (TossAlreadyProcessedException ex) {
			return absorbAlreadyProcessed(paymentKey, orderId, amount, ex);
		}
		return new PaymentConfirmResult(response.paymentKey(), response.orderId(), response.method(),
				response.totalAmount(), toServerTime(response.approvedAt()));
	}

	@Override
	public PaymentCancelResult cancel(String paymentKey, String reason) {
		TossCancelRequest request = new TossCancelRequest(reason);
		String idempotencyKey = CANCEL_IDEMPOTENCY_PREFIX + paymentKey;
		TossPaymentResponse response;
		try {
			response = send(ErrorCode.PAYMENT_CANCEL_FAILED, paymentKey, idempotencyKey, CANCEL_PATH, request,
					paymentKey);
		} catch (TossAlreadyProcessedException ex) {
			// 이미 취소된 결제는 토스가 ALREADY_CANCELED_PAYMENT 로 답해 이 코드로 오지 않는다.
			// 취소 요청에서 나오면 예상 밖의 응답이라 거절로 본다.
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, ex.getMessage());
		}
		TossPaymentResponse.Cancel lastCancel = response.lastCancel();
		LocalDateTime canceledAt = lastCancel == null ? null : toServerTime(lastCancel.canceledAt());
		return new PaymentCancelResult(response.paymentKey(), response.status(), canceledAt);
	}

	@Override
	public PaymentLookupResult lookup(String tossOrderId) {
		try {
			TossPaymentResponse response = restClient.get()
					.uri(LOOKUP_PATH, tossOrderId)
					.header(HttpHeaders.AUTHORIZATION, authorization)
					.retrieve()
					.body(TossPaymentResponse.class);
			if (response == null) {
				log.warn("토스 결제 조회 2xx 빈 응답: orderId={}", tossOrderId);
				throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, "TOSS 조회 응답 본문이 비어 있습니다.");
			}
			return toLookupResult(response);
		} catch (RestClientResponseException ex) {
			TossErrorResponse error = parseError(ex.getResponseBodyAsString());
			boolean notFound = ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
					&& NOT_FOUND_ERROR_CODES.contains(error.code());
			if (notFound) {
				return PaymentLookupResult.notFound();
			}
			log.warn("토스 결제 조회 오류 응답: orderId={}, status={}, tossCode={}, tossMessage={}",
					tossOrderId, ex.getStatusCode(), displayCode(error.code()), error.message());
			throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN,
					"TOSS " + displayCode(error.code()) + ": " + error.message());
		} catch (RestClientException ex) {
			log.warn("토스 결제 조회 통신 실패: orderId={}", tossOrderId, ex);
			throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, "TOSS 통신 실패: " + ex.getMessage());
		}
	}

	private TossPaymentResponse send(ErrorCode errorCode, String paymentKey, String idempotencyKey, String uri,
			Object body, Object... uriVariables) {
		try {
			TossPaymentResponse response = restClient.post()
					.uri(uri, uriVariables)
					.header(HttpHeaders.AUTHORIZATION, authorization)
					.header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(TossPaymentResponse.class);
			if (response == null) {
				log.warn("토스 결제 API 2xx 빈 응답: paymentKey={}", paymentKey);
				throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, "TOSS 응답 본문이 비어 있습니다.");
			}
			return response;
		} catch (RestClientResponseException ex) {
			TossErrorResponse error = parseError(ex.getResponseBodyAsString());
			TossFailureType failureType = TossFailureType.classify(ex.getStatusCode(), error.code());
			String detail = "TOSS " + displayCode(error.code()) + ": " + error.message();
			if (failureType == TossFailureType.ALREADY_PROCESSED) {
				log.warn("토스 결제 API 이미 처리된 승인 요청: paymentKey={}, tossCode={}", paymentKey, error.code());
				throw new TossAlreadyProcessedException(detail);
			}
			if (failureType == TossFailureType.RESULT_UNKNOWN) {
				log.warn("토스 결제 API 결과 불명 응답: paymentKey={}, status={}, tossCode={}, tossMessage={}",
						paymentKey, ex.getStatusCode(), displayCode(error.code()), error.message());
				throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, detail);
			}
			log.warn("토스 결제 API 거절 응답: errorCode={}, paymentKey={}, tossCode={}, tossMessage={}",
					errorCode.name(), paymentKey, displayCode(error.code()), error.message());
			throw new BusinessException(errorCode, detail);
		} catch (RestClientException ex) {
			// 타임아웃·연결 실패는 응답이 없어 토스가 처리했는지 알 수 없다. 거절로 확정하지 않는다.
			log.warn("토스 결제 API 통신 실패: paymentKey={}", paymentKey, ex);
			throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, "TOSS 통신 실패: " + ex.getMessage());
		}
	}

	/** 같은 결제 재전송(이전 응답 유실)이면 토스가 이미 처리했다고 답하므로 조회로 실제 상태를 확인해 성공으로 잇는다. */
	private PaymentConfirmResult absorbAlreadyProcessed(String paymentKey, String orderId, BigDecimal amount,
			TossAlreadyProcessedException cause) {
		PaymentLookupResult lookup = lookup(orderId);
		boolean matches = lookup.status() == PaymentLookupStatus.DONE
				&& paymentKey.equals(lookup.paymentKey())
				&& lookup.totalAmount() != null
				&& amount.compareTo(lookup.totalAmount()) == 0;
		if (!matches) {
			log.error("토스 이미 처리된 승인 요청을 조회로 확정하지 못함: paymentKey={}, orderId={}, lookupStatus={}, "
					+ "lookupPaymentKey={}", paymentKey, orderId, lookup.status(), lookup.paymentKey());
			throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, cause.getMessage());
		}
		return new PaymentConfirmResult(lookup.paymentKey(), orderId, lookup.method(), lookup.totalAmount(),
				lookup.approvedAt());
	}

	private PaymentLookupResult toLookupResult(TossPaymentResponse response) {
		PaymentLookupStatus status = parseLookupStatus(response.status());
		TossPaymentResponse.Cancel lastCancel = response.lastCancel();
		LocalDateTime canceledAt = lastCancel == null ? null : toServerTime(lastCancel.canceledAt());
		return new PaymentLookupResult(status, response.paymentKey(), response.method(), response.totalAmount(),
				toServerTime(response.approvedAt()), canceledAt);
	}

	private PaymentLookupStatus parseLookupStatus(String status) {
		if (status == null) {
			throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, "TOSS 조회 상태 값이 없습니다.");
		}
		try {
			return PaymentLookupStatus.valueOf(status);
		} catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, "TOSS 알 수 없는 조회 상태: " + status);
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

	private String displayCode(String code) {
		return code == null ? UNKNOWN_ERROR_CODE : code;
	}

	private TossErrorResponse parseError(String body) {
		if (body == null || body.isBlank()) {
			return new TossErrorResponse(null, "");
		}
		try {
			return objectMapper.readValue(body, TossErrorResponse.class);
		} catch (JsonProcessingException ex) {
			return new TossErrorResponse(null, body);
		}
	}

	/** send() 내부에서만 오가는 신호. confirm 은 조회로 흡수하고, cancel 은 거절과 동일하게 처리한다. */
	private static final class TossAlreadyProcessedException extends RuntimeException {

		private TossAlreadyProcessedException(String message) {
			super(message);
		}
	}
}
