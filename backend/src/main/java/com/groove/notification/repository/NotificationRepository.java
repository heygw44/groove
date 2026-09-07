package com.groove.notification.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Page<Notification> findAllByMemberId(Long memberId, Pageable pageable);

	Page<Notification> findAllByMemberIdAndReadAtIsNull(Long memberId, Pageable pageable);

	long countByMemberIdAndReadAtIsNull(Long memberId);

	/** 전체 로딩 없이 일괄 UPDATE 로 모두 읽음 처리한다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Notification n set n.readAt = :now where n.member.id = :memberId and n.readAt is null")
	int markAllRead(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
