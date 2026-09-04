package com.groove.order.repository;

import java.time.LocalDateTime;
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

	@Query("""
			select o.id from Order o
			where o.status = :status and o.expiresAt <= :now
			order by o.expiresAt asc, o.id asc
			""")
	List<Long> findIdsByStatusAndExpiresAtBefore(@Param("status") OrderStatus status, @Param("now") LocalDateTime now,
			Limit limit);
}
