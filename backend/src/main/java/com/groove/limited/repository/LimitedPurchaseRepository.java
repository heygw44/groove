package com.groove.limited.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.limited.entity.LimitedPurchase;

public interface LimitedPurchaseRepository extends JpaRepository<LimitedPurchase, Long> {

	boolean existsByDropIdAndMemberId(Long dropId, Long memberId);

	Optional<LimitedPurchase> findByOrderId(Long orderId);

	long countByDropId(Long dropId);

	@Query("select p from LimitedPurchase p join fetch p.member left join fetch p.order where p.drop.id = :dropId "
			+ "order by p.id")
	List<LimitedPurchase> findAllWithMemberAndOrderByDropId(@Param("dropId") Long dropId);
}
