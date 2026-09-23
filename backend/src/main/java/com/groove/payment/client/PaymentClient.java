package com.groove.payment.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentTransaction;

/**
 * 결제 대행사 연동 창구. 명시적 거절은 PAYMENT_CONFIRM_FAILED/PAYMENT_CANCEL_FAILED 로,
 * 결과를 알 수 없으면 PAYMENT_RESULT_UNKNOWN 으로 BusinessException 이 올라온다.
 */
public interface PaymentClient {

	PaymentConfirmResult confirm(String paymentKey, String orderId, BigDecimal amount);

	PaymentCancelResult cancel(String paymentKey, String reason);

	PaymentLookupResult lookup(String tossOrderId);

	List<PaymentTransaction> listTransactions(LocalDateTime from, LocalDateTime to);
}
