package com.groove.notification.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.groove.notification.entity.Notification;
import com.groove.notification.entity.NotificationType;

/** productId/albumId 는 type 에 따라 한쪽만 값을 갖고 나머지는 응답에서 빠진다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
		Long id,
		NotificationType type,
		Long productId,
		Long albumId,
		String titleSnapshot,
		LocalDateTime readAt,
		LocalDateTime createdAt
) {

	public static NotificationResponse from(Notification notification) {
		Long productId = notification.getProduct() == null ? null : notification.getProduct().getId();
		Long albumId = notification.getAlbum() == null ? null : notification.getAlbum().getId();
		return new NotificationResponse(
				notification.getId(),
				notification.getType(),
				productId,
				albumId,
				notification.getTitleSnapshot(),
				notification.getReadAt(),
				notification.getCreatedAt());
	}
}
