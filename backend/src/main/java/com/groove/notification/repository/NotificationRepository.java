package com.groove.notification.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.notification.entity.Notification;
import com.groove.notification.entity.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Page<Notification> findAllByMemberId(Long memberId, Pageable pageable);

	Page<Notification> findAllByMemberIdAndReadAtIsNull(Long memberId, Pageable pageable);

	long countByMemberIdAndReadAtIsNull(Long memberId);

	/** 전체 로딩 없이 일괄 UPDATE 로 모두 읽음 처리한다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Notification n set n.readAt = :now where n.member.id = :memberId and n.readAt is null")
	int markAllRead(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

	/** 파생 메서드는 건건이 SELECT 후 DELETE 라 알림 수만큼 쿼리가 나가 벌크 DELETE 로 대신한다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Notification n where n.member.id = :memberId and n.readAt is not null")
	int deleteAllByMemberIdAndReadAtIsNotNull(@Param("memberId") Long memberId);

	// 후보 회원마다 exists 쿼리를 도는 대신 in-절 한 방으로 "이미 안 읽은 같은 알림을 가진 회원" 만 골라 차집합에 쓴다.
	@Query("""
			select n.member.id from Notification n
			where n.member.id in :memberIds and n.product.id = :productId
				and n.type = :type and n.readAt is null
			""")
	List<Long> findMemberIdsWithUnreadNotification(@Param("memberIds") List<Long> memberIds,
			@Param("productId") Long productId, @Param("type") NotificationType type);

	@Query("""
			select n.member.id from Notification n
			where n.member.id in :memberIds and n.album.id = :albumId
				and n.type = :type and n.readAt is null
			""")
	List<Long> findMemberIdsWithUnreadAlbumNotification(@Param("memberIds") List<Long> memberIds,
			@Param("albumId") Long albumId, @Param("type") NotificationType type);
}
