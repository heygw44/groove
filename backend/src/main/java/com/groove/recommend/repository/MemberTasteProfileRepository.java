package com.groove.recommend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.recommend.entity.MemberTasteProfile;

public interface MemberTasteProfileRepository extends JpaRepository<MemberTasteProfile, Long> {

	Optional<MemberTasteProfile> findByMemberId(Long memberId);
}
