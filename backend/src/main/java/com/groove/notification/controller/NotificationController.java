package com.groove.notification.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;
import com.groove.notification.dto.NotificationResponse;
import com.groove.notification.dto.NotificationSearchRequest;
import com.groove.notification.dto.UnreadCountResponse;
import com.groove.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 알림 조회/읽음 처리. 단건 읽음 처리(PATCH /notifications/{id}/read)만 다른 엔드포인트와 달리
 * {@code /members/me} 밖에 있어 클래스 레벨 매핑을 {@code /api/v1} 로 두고 메서드마다 전체 경로를 적는다.
 */
@Tag(name = "Notification", description = "알림")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@Operation(summary = "내 알림 목록 조회")
	@GetMapping("/members/me/notifications")
	public ApiResponse<PageResponse<NotificationResponse>> getMyNotifications(@AuthMember LoginMember loginMember,
			@Valid @ModelAttribute NotificationSearchRequest request) {
		return ApiResponse.ok(notificationService.getMyNotifications(loginMember.id(), request));
	}

	@Operation(summary = "안 읽은 알림 개수 조회")
	@GetMapping("/members/me/notifications/unread-count")
	public ApiResponse<UnreadCountResponse> getUnreadCount(@AuthMember LoginMember loginMember) {
		return ApiResponse.ok(notificationService.getUnreadCount(loginMember.id()));
	}

	@Operation(summary = "알림 읽음 처리")
	@PatchMapping("/notifications/{id}/read")
	public ApiResponse<Void> markRead(@AuthMember LoginMember loginMember, @PathVariable Long id) {
		notificationService.markRead(loginMember.id(), id);
		return ApiResponse.ok();
	}

	@Operation(summary = "알림 전체 읽음 처리")
	@PatchMapping("/members/me/notifications/read-all")
	public ApiResponse<Void> markAllRead(@AuthMember LoginMember loginMember) {
		notificationService.markAllRead(loginMember.id());
		return ApiResponse.ok();
	}
}
