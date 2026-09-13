package com.groove.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.payment.entity.PaymentReconcileLog;

public interface PaymentReconcileLogRepository extends JpaRepository<PaymentReconcileLog, Long> {

	boolean existsByPaymentIdAndTossStatus(Long paymentId, String tossStatus);

	List<PaymentReconcileLog> findByPaymentIdOrderByIdAsc(Long paymentId);
}
