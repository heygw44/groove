package com.groove.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.payment.dto.PaymentCancelTarget;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByOrderId(Long orderId);

	Optional<Payment> findByPaymentKey(String paymentKey);

	Optional<Payment> findByTossOrderId(String tossOrderId);

	/** order 는 LAZY 라 candidate 생성 시 getOrder().getId() 는 프록시 id 만 읽어 추가 SQL 을 내지 않는다. */
	List<Payment> findByApprovedAtGreaterThanEqualAndApprovedAtLessThan(LocalDateTime from, LocalDateTime to);

	@Query("""
			select new com.groove.payment.dto.PaymentCancelTarget(p.order.id, p.status)
			from Payment p
			where p.id = :id and p.order.member.id = :memberId
			""")
	Optional<PaymentCancelTarget> findCancelTarget(@Param("id") Long id, @Param("memberId") Long memberId);

	@Query("""
			select new com.groove.payment.dto.PaymentReconcileCandidate(p.id, p.order.id, p.tossOrderId)
			from Payment p
			where p.status in :statuses and p.updatedAt < :before and p.reconcileAttempts < :maxAttempts
			order by p.updatedAt asc, p.id asc
			""")
	List<PaymentReconcileCandidate> findReconcileCandidates(@Param("statuses") List<PaymentStatus> statuses,
			@Param("before") LocalDateTime before, @Param("maxAttempts") int maxAttempts, Limit limit);
}
