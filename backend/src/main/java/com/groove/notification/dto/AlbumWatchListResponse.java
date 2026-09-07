package com.groove.notification.dto;

import java.util.List;

public record AlbumWatchListResponse(
		List<AlbumWatchResponse> content
) {

	public static AlbumWatchListResponse of(List<AlbumWatchResponse> content) {
		return new AlbumWatchListResponse(content);
	}
}
