package com.groove.payment.service;

/** {@link PaymentSettlementService#reconcile} 결과 집계. */
public record PaymentSettlementReport(int transactions, int matched, int applied, int mismatched, int unknown,
		int failed) {
}
