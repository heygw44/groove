package com.groove.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByOrderId(Long orderId);

	Optional<Payment> findByPaymentKey(String paymentKey);

	Optional<Payment> findByIdAndOrderMemberId(Long id, Long memberId);

	@Query("""
			select new com.groove.payment.dto.PaymentReconcileCandidate(p.id, p.order.id, p.tossOrderId)
			from Payment p
			where p.status in :statuses and p.updatedAt < :before and p.reconcileAttempts < :maxAttempts
			order by p.updatedAt asc, p.id asc
			""")
	List<PaymentReconcileCandidate> findReconcileCandidates(@Param("statuses") List<PaymentStatus> statuses,
			@Param("before") LocalDateTime before, @Param("maxAttempts") int maxAttempts, Limit limit);
}
