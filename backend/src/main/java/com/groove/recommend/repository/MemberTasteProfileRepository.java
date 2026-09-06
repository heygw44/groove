package com.groove.recommend.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.recommend.entity.MemberTasteProfile;

public interface MemberTasteProfileRepository extends JpaRepository<MemberTasteProfile, Long> {

	Optional<MemberTasteProfile> findByMemberId(Long memberId);

	/**
	 * 취향은 조인 테이블만 바뀌어 프로필 행이 dirty 가 되지 않는다. 그래서 @LastModifiedDate 가 돌지 않고,
	 * 갱신 시각을 직접 써 준다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update MemberTasteProfile p set p.updatedAt = :now where p.id = :profileId")
	void touchUpdatedAt(@Param("profileId") Long profileId, @Param("now") LocalDateTime now);
}
