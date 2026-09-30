package com.groove.payment.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.groove.order.service.OrderClaimRefundReader;
import com.groove.payment.entity.PaymentCancelStatus;
import com.groove.payment.repository.PaymentCancelRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentCancelClaimReader implements OrderClaimRefundReader {

	private final PaymentCancelRepository paymentCancelRepository;

	@Override
	public boolean hasPendingRefund(Long orderClaimId) {
		return paymentCancelRepository.existsByOrderClaimIdAndStatusIn(orderClaimId,
				Set.of(PaymentCancelStatus.REQUESTED));
	}

	@Override
	public boolean hasAnyRefund(Long orderClaimId) {
		return paymentCancelRepository.existsByOrderClaimId(orderClaimId);
	}

	@Override
	public Set<Long> findPendingRefundOrderItemIds(Collection<Long> orderItemIds) {
		if (orderItemIds.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(paymentCancelRepository.findPendingRefundOrderItemIds(orderItemIds));
	}
}
