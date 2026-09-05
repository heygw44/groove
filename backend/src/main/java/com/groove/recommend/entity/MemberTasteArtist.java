package com.groove.recommend.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import com.groove.product.entity.Artist;

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

/** 취향 프로필의 선호 아티스트. 프로필이 먼저 저장돼 id 를 가진 뒤에 만든다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "member_taste_artist",
		indexes = @Index(name = "idx_taste_artist_artist", columnList = "artist_id"))
public class MemberTasteArtist {

	@EmbeddedId
	private MemberTasteArtistId id;

	@MapsId("profileId")
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "profile_id", foreignKey = @ForeignKey(name = "fk_taste_artist_profile"))
	private MemberTasteProfile profile;

	@MapsId("artistId")
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "artist_id", foreignKey = @ForeignKey(name = "fk_taste_artist_artist"))
	private Artist artist;

	private MemberTasteArtist(MemberTasteArtistId id, MemberTasteProfile profile, Artist artist) {
		this.id = id;
		this.profile = profile;
		this.artist = artist;
	}

	public static MemberTasteArtist of(MemberTasteProfile profile, Artist artist) {
		MemberTasteArtistId id = MemberTasteArtistId.of(profile.getId(), artist.getId());
		return new MemberTasteArtist(id, profile, artist);
	}
}
