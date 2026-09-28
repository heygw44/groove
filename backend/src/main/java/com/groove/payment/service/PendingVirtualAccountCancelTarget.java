package com.groove.payment.service;

/** {@link PendingVirtualAccountCancelWriter#lock} 결과. */
record PendingVirtualAccountCancelTarget(Long paymentId, String paymentKey) {
}
