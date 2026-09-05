package com.groove.recommend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.recommend.entity.MemberTasteArtist;
import com.groove.recommend.entity.MemberTasteArtistId;

public interface MemberTasteArtistRepository extends JpaRepository<MemberTasteArtist, MemberTasteArtistId> {

	@EntityGraph(attributePaths = "artist")
	List<MemberTasteArtist> findAllByProfileId(Long profileId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from MemberTasteArtist a where a.profile.id = :profileId")
	void deleteAllByProfileId(@Param("profileId") Long profileId);

	/**
	 * 취향은 부분 갱신 없이 전체 교체한다. 키가 미리 채워져 있어 saveAll 은 merge 로 가며 항목당 SELECT 한 번이 난다(최대 5건).
	 */
	default void replaceAll(Long profileId, List<MemberTasteArtist> items) {
		deleteAllByProfileId(profileId);
		saveAll(items);
	}
}
