package com.groove.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.payment.entity.PaymentCancel;
import com.groove.payment.entity.PaymentCancelStatus;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

	long countByPaymentId(Long paymentId);

	List<PaymentCancel> findByPaymentIdOrderByIdAsc(Long paymentId);

	Optional<PaymentCancel> findByIdempotencyKey(String idempotencyKey);

	/** 부분취소·전액취소가 서로 겹쳐 들어오지 않게 두 진입점 모두 이 존재 여부부터 확인한다. */
	boolean existsByPaymentIdAndStatus(Long paymentId, PaymentCancelStatus status);
}
