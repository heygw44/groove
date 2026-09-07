package com.groove.limited.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.limited.entity.LimitedDropStat;

public interface LimitedDropStatRepository extends JpaRepository<LimitedDropStat, Long> {

	Optional<LimitedDropStat> findByDropId(Long dropId);
}
