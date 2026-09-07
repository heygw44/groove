package com.groove.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.notification.dto.NotificationResponse;
import com.groove.notification.dto.NotificationSearchRequest;
import com.groove.notification.dto.UnreadCountResponse;
import com.groove.notification.entity.Notification;
import com.groove.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/** 알림 조회/읽음 처리. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final Clock clock;

	public PageResponse<NotificationResponse> getMyNotifications(Long memberId, NotificationSearchRequest request) {
		Page<Notification> page = request.isUnreadOnly()
				? notificationRepository.findAllByMemberIdAndReadAtIsNull(memberId, request.toPageable())
				: notificationRepository.findAllByMemberId(memberId, request.toPageable());
		return PageResponse.from(page.map(NotificationResponse::from));
	}

	public UnreadCountResponse getUnreadCount(Long memberId) {
		return UnreadCountResponse.of(notificationRepository.countByMemberIdAndReadAtIsNull(memberId));
	}

	@Transactional
	public void markRead(Long memberId, Long notificationId) {
		Notification notification = notificationRepository.findById(notificationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
		if (!notification.getMember().getId().equals(memberId)) {
			throw new BusinessException(ErrorCode.NOTIFICATION_FORBIDDEN);
		}
		notification.markRead(LocalDateTime.now(clock));
	}

	@Transactional
	public void markAllRead(Long memberId) {
		notificationRepository.markAllRead(memberId, LocalDateTime.now(clock));
	}

	@Transactional
	public void delete(Long memberId, Long notificationId) {
		Notification notification = notificationRepository.findById(notificationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
		if (!notification.getMember().getId().equals(memberId)) {
			throw new BusinessException(ErrorCode.NOTIFICATION_FORBIDDEN);
		}
		notificationRepository.delete(notification);
	}

	@Transactional
	public void deleteRead(Long memberId) {
		notificationRepository.deleteAllByMemberIdAndReadAtIsNotNull(memberId);
	}
}
