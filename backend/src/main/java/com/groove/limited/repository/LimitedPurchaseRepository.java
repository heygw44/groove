package com.groove.limited.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.limited.entity.LimitedPurchase;

public interface LimitedPurchaseRepository extends JpaRepository<LimitedPurchase, Long> {

	boolean existsByDropIdAndMemberId(Long dropId, Long memberId);

	Optional<LimitedPurchase> findByOrderId(Long orderId);
}
