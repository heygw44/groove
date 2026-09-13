package com.groove.order.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.payment.entity.PaymentStatus;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {

	Optional<Order> findByOrderNumber(String orderNumber);

	Optional<Order> findByIdAndMemberId(Long id, Long memberId);

	@EntityGraph(attributePaths = {"items", "items.product", "memberCoupon", "memberCoupon.coupon"})
	Optional<Order> findWithItemsById(Long id);

	@EntityGraph(attributePaths = {"items", "items.product", "memberCoupon", "memberCoupon.coupon"})
	Optional<Order> findWithItemsByIdAndMemberId(Long id, Long memberId);

	@EntityGraph(attributePaths = {"items", "items.product", "member", "memberCoupon", "memberCoupon.coupon"})
	Optional<Order> findWithItemsAndMemberById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Order o where o.id = :id")
	Optional<Order> findByIdForUpdate(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Order o where o.orderNumber = :orderNumber")
	Optional<Order> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

	// 결제가 READY/UNKNOWN 인 주문은 대사가 결론을 낼 때까지 만료 대상에서 뺀다 - 안 그러면 스킵되는 행이
	// 배치 슬롯을 계속 차지해 만료할 다른 주문이 굶는다. NOT EXISTS 서브쿼리 대신 LEFT JOIN ... IS NULL 로
	// 쓴 이유: MySQL 이 전자를 semijoin materialization 으로 풀어 payment 를 전부 훑는다. 후자는 uk_payment_order 를 탄다.
	@Query("""
			select o.id from Order o
			left join Payment p on p.order.id = o.id and p.status in :unresolvedPaymentStatuses
			where o.status = :status and o.expiresAt <= :now and p.id is null
			order by o.expiresAt asc, o.id asc
			""")
	List<Long> findIdsByStatusAndExpiresAtBefore(@Param("status") OrderStatus status, @Param("now") LocalDateTime now,
			@Param("unresolvedPaymentStatuses") Collection<PaymentStatus> unresolvedPaymentStatuses, Limit limit);
}
