package com.groove.notification.dto;

public record UnreadCountResponse(long count) {

	public static UnreadCountResponse of(long count) {
		return new UnreadCountResponse(count);
	}
}
