package com.groove.recommend.dto;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.groove.product.dto.ArtistResponse;
import com.groove.product.dto.GenreResponse;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.recommend.entity.Decade;
import com.groove.recommend.entity.MemberTasteArtist;
import com.groove.recommend.entity.MemberTasteDecade;
import com.groove.recommend.entity.MemberTasteGenre;

public record TasteProfileResponse(
		List<GenreResponse> genres,
		List<ArtistResponse> artists,
		List<Decade> decades,
		LocalDateTime updatedAt
) {

	/** 조인 테이블 조회 순서는 보장되지 않아 id·enum 선언 순으로 정렬해 내보낸다. */
	public static TasteProfileResponse of(List<MemberTasteGenre> genres, List<MemberTasteArtist> artists,
			List<MemberTasteDecade> decades, LocalDateTime updatedAt) {
		return new TasteProfileResponse(
				genres.stream()
						.map(MemberTasteGenre::getGenre)
						.sorted(Comparator.comparing(Genre::getId))
						.map(GenreResponse::from)
						.toList(),
				artists.stream()
						.map(MemberTasteArtist::getArtist)
						.sorted(Comparator.comparing(Artist::getId))
						.map(ArtistResponse::from)
						.toList(),
				decades.stream()
						.map(MemberTasteDecade::getDecade)
						.sorted()
						.toList(),
				updatedAt);
	}
}
