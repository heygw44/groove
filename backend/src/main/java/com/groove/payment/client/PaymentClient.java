package com.groove.payment.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

	/** 환불계좌가 필요 없는 취소(카드 환불, 입금 전 가상계좌 폐쇄 등) 전용. */
	default PaymentCancelResult cancel(String paymentKey, String reason) {
		return cancel(paymentKey, reason, null);
	}

	/** 입금된 가상계좌를 환불할 때는 refundAccount 를 채워야 한다. */
	PaymentCancelResult cancel(String paymentKey, String reason, RefundAccountInfo refundAccount);

	PaymentLookupResult lookup(String tossOrderId);

	List<PaymentTransaction> listTransactions(LocalDateTime from, LocalDateTime to);
}
