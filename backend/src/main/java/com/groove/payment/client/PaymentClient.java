package com.groove.payment.client;

import java.math.BigDecimal;

import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentConfirmResult;

/** 결제 대행사 연동 창구. 승인/취소 실패는 모두 BusinessException 으로 올라온다. */
public interface PaymentClient {

	PaymentConfirmResult confirm(String paymentKey, String orderId, BigDecimal amount);

	PaymentCancelResult cancel(String paymentKey, String reason);
}
