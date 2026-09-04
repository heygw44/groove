package com.groove.limited.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.limited.entity.LimitedPurchase;

public interface LimitedPurchaseRepository extends JpaRepository<LimitedPurchase, Long> {

	boolean existsByDropIdAndMemberId(Long dropId, Long memberId);
}
