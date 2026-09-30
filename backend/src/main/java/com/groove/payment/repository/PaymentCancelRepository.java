package com.groove.payment.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.payment.dto.PaymentCancelRetryCandidate;
import com.groove.payment.entity.PaymentCancel;
import com.groove.payment.entity.PaymentCancelStatus;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

	long countByPaymentId(Long paymentId);

	List<PaymentCancel> findByPaymentIdOrderByIdAsc(Long paymentId);

	Optional<PaymentCancel> findByIdempotencyKey(String idempotencyKey);

	Optional<PaymentCancel> findFirstByPaymentIdAndStatusOrderByIdDesc(Long paymentId, PaymentCancelStatus status);

	/** 부분취소·전액취소가 서로 겹쳐 들어오지 않게 두 진입점 모두 이 존재 여부부터 확인한다. */
	boolean existsByPaymentIdAndStatus(Long paymentId, PaymentCancelStatus status);

	boolean existsByOrderClaimId(Long orderClaimId);

	boolean existsByOrderClaimIdAndStatusIn(Long orderClaimId, Collection<PaymentCancelStatus> statuses);

	/** 클레임 환불이 결과를 기다리는(REQUESTED) 상품주문 id. 구매자 화면의 "환불 처리 중" 표시에 쓴다. */
	@Query("""
			select distinct c.orderItem.id from PaymentCancel pc, com.groove.order.entity.OrderClaim c
			where c.id = pc.orderClaimId
			and c.orderItem.id in :orderItemIds
			and pc.status = com.groove.payment.entity.PaymentCancelStatus.REQUESTED
			""")
	List<Long> findPendingRefundOrderItemIds(@Param("orderItemIds") Collection<Long> orderItemIds);

	/**
	 * 결과불명으로 REQUESTED 에 남아 requestedAt 이 오래된 부분취소 재시도 후보. payment.status 를
	 * DONE/PARTIAL_CANCELED 로 좁히는 이유: 전액취소(PaymentCancelWriter)의 REQUESTED 행은 결제가 항상
	 * CANCEL_REQUESTED 인 동안만 존재하고, 그 재시도는 이미 대사 스케줄러의 기존 취소 재시도 경로가 매 주기
	 * 맡고 있어 여기서 다시 집어가면 안 된다.
	 */
	@Query("""
			select new com.groove.payment.dto.PaymentCancelRetryCandidate(pc.id, p.id, p.paymentKey, p.tossOrderId,
					pc.cancelAmount, pc.idempotencyKey, pc.reason, pc.requestedAt, pc.orderClaimId)
			from PaymentCancel pc join pc.payment p
			where pc.status = com.groove.payment.entity.PaymentCancelStatus.REQUESTED
			and p.status in (com.groove.payment.entity.PaymentStatus.DONE,
				com.groove.payment.entity.PaymentStatus.PARTIAL_CANCELED)
			and pc.requestedAt <= :retryBefore
			order by pc.requestedAt asc, pc.id asc
			""")
	List<PaymentCancelRetryCandidate> findRetryCandidates(@Param("retryBefore") LocalDateTime retryBefore,
			Limit limit);
}
