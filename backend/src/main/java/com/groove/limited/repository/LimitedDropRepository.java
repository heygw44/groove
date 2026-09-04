package com.groove.limited.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;

public interface LimitedDropRepository extends JpaRepository<LimitedDrop, Long> {

	Optional<LimitedDrop> findByProductId(Long productId);

	boolean existsByProductIdAndStatusNot(Long productId, LimitedDropStatus status);
}
