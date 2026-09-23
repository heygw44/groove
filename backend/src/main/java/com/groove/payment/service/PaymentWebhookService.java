package com.groove.payment.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.dto.PaymentCompensationCandidate;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.dto.PaymentWebhookRequest;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentCompensation;
import com.groove.payment.entity.PaymentCompensationStatus;
import com.groove.payment.entity.PaymentWebhookEvent;
import com.groove.payment.entity.PaymentWebhookResult;
import com.groove.payment.repository.PaymentCompensationRepository;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 토스 웹훅(PAYMENT_STATUS_CHANGED)을 받아, 대사 재시도 상한을 넘겨 FAILED 로 확정된 결제도 다시 대사에
 * 넣는다. 서명 헤더가 없는 요청이라 본문의 status 는 멱등 키·감사 용도로만 쓰고, 실제 판단은 항상
 * {@link PaymentClient#lookup} 재조회 결과로 한다.
 *
 * <p>무인증 엔드포인트라 누구나 호출할 수 있어, 우리가 아는 결제(payment)나 보상 대기 행
 * (payment_compensation)에 매칭되는 이벤트만 DB에 남긴다 — 파싱 실패·대상 아닌 이벤트·모르는 orderId 는
 * 로그만 남기고 행을 만들지 않는다. 재조회·적용에 실패하면 이벤트 행을 ERROR 로 남긴 뒤 예외를 던져
 * 컨트롤러가 200 이 아닌 응답을 주게 한다 — 그래야 토스가 재전송한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

	private static final String TARGET_EVENT_TYPE = "PAYMENT_STATUS_CHANGED";

	private final ObjectMapper objectMapper;
	private final PaymentWebhookEventWriter eventWriter;
	private final PaymentRepository paymentRepository;
	private final PaymentCompensationRepository compensationRepository;
	private final PaymentCompensationRetrier compensationRetrier;
	private final PaymentClient paymentClient;
	private final PaymentLateResultApplier lateResultApplier;
	private final AlertNotifier alertNotifier;

	public void handle(String rawBody) {
		PaymentWebhookRequest request;
		try {
			request = objectMapper.readValue(rawBody, PaymentWebhookRequest.class);
		} catch (Exception ex) {
			log.warn("토스 웹훅 본문 파싱 실패, 저장하지 않음: {}", ex.getMessage());
			return;
		}
		if (!TARGET_EVENT_TYPE.equals(request.eventType())) {
			log.warn("토스 웹훅 대상 이벤트 아님, 저장하지 않음: eventType={}", request.eventType());
			return;
		}

		PaymentWebhookRequest.Data data = request.data();
		String paymentKey = data == null ? null : data.paymentKey();
		String tossOrderId = data == null ? null : data.orderId();
		String tossStatus = data == null ? null : data.status();
		if (tossOrderId == null) {
			log.warn("토스 웹훅 data.orderId 없음, 저장하지 않음: paymentKey={}", paymentKey);
			return;
		}

		Optional<Payment> payment = paymentRepository.findByTossOrderId(tossOrderId);
		if (payment.isPresent()) {
			handleKnownPayment(request, paymentKey, tossOrderId, tossStatus, payment.get());
			return;
		}

		Optional<PaymentCompensation> compensation = paymentKey == null ? Optional.empty()
				: compensationRepository.findByPaymentKey(paymentKey);
		if (compensation.isPresent() && compensation.get().getStatus() == PaymentCompensationStatus.PENDING) {
			handlePendingCompensation(request, paymentKey, tossOrderId, tossStatus, compensation.get());
			return;
		}
		log.warn("토스 웹훅 결제·보상 대기 행 없음, 저장하지 않음: tossOrderId={}, paymentKey={}", tossOrderId, paymentKey);
	}

	private void handleKnownPayment(PaymentWebhookRequest request, String paymentKey, String tossOrderId,
			String tossStatus, Payment payment) {
		Optional<PaymentWebhookEvent> event = eventWriter.receive(request.eventType(), paymentKey, tossOrderId,
				tossStatus, request.createdAt());
		if (event.isEmpty()) {
			return;
		}
		resolve(event.get().getId(), payment, tossOrderId);
	}

	/** payment 행이 없으면 uk_payment_order 때문에 결제 행을 못 만든 payment_compensation 대기 큐를 대신 회수한다. */
	private void handlePendingCompensation(PaymentWebhookRequest request, String paymentKey, String tossOrderId,
			String tossStatus, PaymentCompensation compensation) {
		Optional<PaymentWebhookEvent> event = eventWriter.receive(request.eventType(), paymentKey, tossOrderId,
				tossStatus, request.createdAt());
		if (event.isEmpty()) {
			return;
		}
		compensationRetrier.retry(new PaymentCompensationCandidate(paymentKey, compensation.getReason()));
		eventWriter.markResult(event.get().getId(), PaymentWebhookResult.APPLIED, "payment_compensation 즉시 회수");
	}

	/**
	 * FAILED 결제도 여기서는 허용한다({@link PaymentReconcileService#applyLate}). 진행 중인 confirm·
	 * 스케줄러 대사와의 경합은 PaymentReconcileService.apply 내부의 주문 FOR UPDATE 락과 Payment.@Version
	 * 이 막는다 — 셋 다 같은 주문 행을 잠그려 하므로 이 서비스는 별도 named lock 을 쓰지 않는다. grace(대사
	 * 후보 조회 전용 유예)도 적용하지 않는다 — 여기서는 findCandidates 를 거치지 않고 웹훅이 지목한 건 하나만
	 * 즉시 재조회한다.
	 *
	 * <p>재조회·적용이 실패해도 recordFailure 로 미스 카운트를 올리지 않는다 — reconcile_attempts 는
	 * 폴링 대사 몫이고, 여기서 올리면 웹훅이 실패할 때마다 상한에 헛되이 가까워진다. 대신 이벤트를 ERROR 로
	 * 남기고 예외를 던져 컨트롤러가 200 이 아닌 응답을 주게 해 토스 재전송에 맡긴다 — 재전송이 오면
	 * {@link PaymentWebhookEventWriter#receive} 가 이 ERROR 행을 재사용해 다시 처리한다.</p>
	 */
	private void resolve(Long eventId, Payment payment, String tossOrderId) {
		PaymentReconcileCandidate candidate = new PaymentReconcileCandidate(payment.getId(),
				payment.getOrder().getId(), tossOrderId);
		PaymentLookupResult lookup;
		try {
			lookup = paymentClient.lookup(tossOrderId);
		} catch (RuntimeException ex) {
			log.warn("토스 웹훅 재조회 실패: tossOrderId={}", tossOrderId, ex);
			eventWriter.markResult(eventId, PaymentWebhookResult.ERROR, "재조회 실패: " + ex.getMessage());
			throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, ex.getMessage());
		}
		try {
			lateResultApplier.apply(candidate, lookup, "webhook");
			eventWriter.markResult(eventId, PaymentWebhookResult.APPLIED, "webhook");
		} catch (RuntimeException ex) {
			log.error("토스 웹훅 대사 적용 실패: paymentId={}, orderId={}", payment.getId(), payment.getOrder().getId(), ex);
			alertNotifier.notify(Alert.warn("payment.webhook-apply-failed",
					"토스 웹훅 대사 적용 실패: paymentId=" + payment.getId() + ", orderId=" + payment.getOrder().getId(),
					"paymentId=" + payment.getId()));
			eventWriter.markResult(eventId, PaymentWebhookResult.ERROR, "적용 실패: " + ex.getMessage());
			throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, ex.getMessage());
		}
	}
}
