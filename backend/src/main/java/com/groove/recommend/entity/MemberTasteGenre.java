package com.groove.recommend.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import com.groove.product.entity.Genre;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 취향 프로필의 선호 장르. 프로필이 먼저 저장돼 id 를 가진 뒤에 만든다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "member_taste_genre",
		indexes = @Index(name = "idx_taste_genre_genre", columnList = "genre_id"))
public class MemberTasteGenre {

	@EmbeddedId
	private MemberTasteGenreId id;

	@MapsId("profileId")
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "profile_id", foreignKey = @ForeignKey(name = "fk_taste_genre_profile"))
	private MemberTasteProfile profile;

	@MapsId("genreId")
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "genre_id", foreignKey = @ForeignKey(name = "fk_taste_genre_genre"))
	private Genre genre;

	private MemberTasteGenre(MemberTasteGenreId id, MemberTasteProfile profile, Genre genre) {
		this.id = id;
		this.profile = profile;
		this.genre = genre;
	}

	public static MemberTasteGenre of(MemberTasteProfile profile, Genre genre) {
		MemberTasteGenreId id = MemberTasteGenreId.of(profile.getId(), genre.getId());
		return new MemberTasteGenre(id, profile, genre);
	}
}
