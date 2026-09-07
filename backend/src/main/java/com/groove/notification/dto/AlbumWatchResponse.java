package com.groove.notification.dto;

import java.time.LocalDateTime;

import com.groove.notification.entity.AlbumWatch;

public record AlbumWatchResponse(
		Long id,
		Long albumId,
		String albumTitle,
		LocalDateTime createdAt
) {

	public static AlbumWatchResponse from(AlbumWatch albumWatch) {
		return new AlbumWatchResponse(albumWatch.getId(), albumWatch.getAlbum().getId(),
				albumWatch.getAlbum().getTitle(), albumWatch.getCreatedAt());
	}
}
