package com.groove.payment.service;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.dto.PaymentConfirmResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 승인 진입점. 토스 API 호출을 DB 트랜잭션 밖에서 하기 위해 이 클래스는 트랜잭션을 걸지 않는다.
 * 승인 실패를 {@code Payment.fail()} 로 저장하면서 예외도 그대로 던져야 하는데, 같은 트랜잭션 안에 두면
 * 실패 기록까지 롤백되기 때문이다. 토스 응답을 알 수 없거나(타임아웃 등) 승인 반영 자체가 실패하면 결제를
 * UNKNOWN 으로 남겨 대사 대상에 올린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

	private final PaymentConfirmWriter writer;
	private final PaymentClient paymentClient;

	public PaymentConfirmResponse confirm(Long memberId, PaymentConfirmRequest request) {
		ConfirmPreparation preparation = writer.prepare(memberId, request);
		if (preparation.alreadyApproved().isPresent()) {
			return preparation.alreadyApproved().get();
		}

		PaymentConfirmResult result = requestTossConfirm(preparation, request);
		return applyApproval(preparation, request, result);
	}

	private PaymentConfirmResult requestTossConfirm(ConfirmPreparation preparation, PaymentConfirmRequest request) {
		try {
			return paymentClient.confirm(request.paymentKey(), preparation.orderNumber(), preparation.finalAmount());
		} catch (BusinessException ex) {
			recordTossFailure(preparation.paymentId(), ex);
			throw ex;
		}
	}

	private PaymentConfirmResponse applyApproval(ConfirmPreparation preparation, PaymentConfirmRequest request,
			PaymentConfirmResult result) {
		Long paymentId = preparation.paymentId();
		try {
			return writer.approve(paymentId, request.paymentKey(), result);
		} catch (BusinessException ex) {
			safeFail(paymentId, ex.getMessage());
			throw ex;
		} catch (RuntimeException ex) {
			throw recoverFromApprovalFailure(paymentId, ex);
		}
	}

	private void recordTossFailure(Long paymentId, BusinessException ex) {
		if (ex.getErrorCode() == ErrorCode.PAYMENT_RESULT_UNKNOWN) {
			safeMarkUnknown(paymentId, ex.getMessage());
		} else {
			safeFail(paymentId, ex.getMessage());
		}
	}

	/**
	 * 토스는 이미 청구를 반영했는데 DB 오류(커넥션 단절, 락 타임아웃, 낙관적 락 충돌 등)로 승인 반영에
	 * 실패한 경우다. READY 로 방치하면 이후 조회로 진행 상태를 알 수 없으므로 UNKNOWN 으로 남겨 대사
	 * 대상에 올린다. 이 기록조차 실패하면 결제는 READY 로 남지만, 대사는 READY 도 조회 대상에 포함한다.
	 */
	private BusinessException recoverFromApprovalFailure(Long paymentId, RuntimeException cause) {
		log.error("결제 승인 반영 실패: paymentId={}", paymentId, cause);
		String reason = "승인 반영 실패: " + cause.getClass().getSimpleName();
		try {
			writer.markUnknown(paymentId, reason);
		} catch (RuntimeException markUnknownFailure) {
			log.error("결과 불명 기록도 실패함: paymentId={}", paymentId, markUnknownFailure);
		}
		return new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, reason);
	}

	/**
	 * 버전 충돌은 동시에 승인이 먼저 커밋돼 DONE 이 이긴 경우다. writer 메서드 안에서 잡으면 커밋 시점에
	 * 다시 터져 트랜잭션이 롤백 전용이 되므로, 커밋이 끝난 뒤인 호출자(여기)에서 잡아야 한다.
	 */
	private void safeFail(Long paymentId, String reason) {
		try {
			writer.fail(paymentId, reason);
		} catch (ObjectOptimisticLockingFailureException ex) {
			log.info("실패 기록 중 이미 승인이 먼저 커밋됨: paymentId={}", paymentId);
		}
	}

	private void safeMarkUnknown(Long paymentId, String reason) {
		try {
			writer.markUnknown(paymentId, reason);
		} catch (ObjectOptimisticLockingFailureException ex) {
			log.info("결과 불명 기록 중 이미 승인이 먼저 커밋됨: paymentId={}", paymentId);
		}
	}
}
