package com.groove.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.notification.entity.AlbumWatch;

public interface AlbumWatchRepository extends JpaRepository<AlbumWatch, Long> {

	boolean existsByMemberIdAndAlbumId(Long memberId, Long albumId);

	Optional<AlbumWatch> findByMemberIdAndAlbumId(Long memberId, Long albumId);

	/** 목록 응답에 albumTitle 이 필요해 N+1 방지용으로 album 을 함께 로딩한다. */
	@EntityGraph(attributePaths = "album")
	List<AlbumWatch> findAllByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);
}
