package com.groove.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.payment.dto.PaymentCompensationCandidate;
import com.groove.payment.entity.PaymentCompensation;

public interface PaymentCompensationRepository extends JpaRepository<PaymentCompensation, Long> {

	Optional<PaymentCompensation> findByPaymentKey(String paymentKey);

	@Query("""
			select new com.groove.payment.dto.PaymentCompensationCandidate(c.paymentKey, c.reason)
			from PaymentCompensation c
			where c.status = com.groove.payment.entity.PaymentCompensationStatus.PENDING
			and c.updatedAt < :before and c.attempts < :maxAttempts
			order by c.updatedAt asc, c.id asc
			""")
	List<PaymentCompensationCandidate> findCandidates(@Param("before") LocalDateTime before,
			@Param("maxAttempts") int maxAttempts, Limit limit);
}
