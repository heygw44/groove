package com.groove.payment.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.groove.payment.client.dto.PaymentCancelCommand;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentTransaction;
import com.groove.payment.client.dto.RefundAccountInfo;

/**
 * 결제 대행사 연동 창구. 명시적 거절은 PAYMENT_CONFIRM_FAILED/PAYMENT_CANCEL_FAILED 로,
 * 결과를 알 수 없으면 PAYMENT_RESULT_UNKNOWN 으로 BusinessException 이 올라온다.
 */
public interface PaymentClient {

	PaymentConfirmResult confirm(String paymentKey, String orderId, BigDecimal amount);

	/**
	 * 결제 취소 요청 행이 없는 전액취소(보상 취소, 입금 전 가상계좌 폐쇄) 전용. 고정 멱등키를 쓴다 -
	 * 이 경로들은 재시도가 첫 시도와 같은 키로 다시 불러야 한다.
	 */
	default PaymentCancelResult cancel(String paymentKey, String reason) {
		return cancel(paymentKey, reason, null);
	}

	/** 입금된 가상계좌를 환불할 때는 refundAccount 를 채워야 한다. 요청 행이 없는 경로 전용, 멱등키 고정. */
	default PaymentCancelResult cancel(String paymentKey, String reason, RefundAccountInfo refundAccount) {
		return cancel(PaymentCancelCommand.fullCancelLegacyKey(paymentKey, reason, refundAccount));
	}

	/** 부분취소를 포함한 일반 취소. 멱등키는 호출자가 취소 건마다 새로 발급해 넘긴다. */
	PaymentCancelResult cancel(PaymentCancelCommand command);

	PaymentLookupResult lookup(String tossOrderId);

	List<PaymentTransaction> listTransactions(LocalDateTime from, LocalDateTime to);
}
