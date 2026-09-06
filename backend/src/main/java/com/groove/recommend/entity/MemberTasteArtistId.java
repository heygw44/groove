package com.groove.recommend.entity;

import static lombok.AccessLevel.PROTECTED;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@link MemberTasteArtist} 복합 키. */
@Embeddable
@Getter
@NoArgsConstructor(access = PROTECTED)
public class MemberTasteArtistId implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long profileId;

	private Long artistId;

	private MemberTasteArtistId(Long profileId, Long artistId) {
		this.profileId = profileId;
		this.artistId = artistId;
	}

	public static MemberTasteArtistId of(Long profileId, Long artistId) {
		return new MemberTasteArtistId(profileId, artistId);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MemberTasteArtistId that)) {
			return false;
		}
		return Objects.equals(profileId, that.profileId)
				&& Objects.equals(artistId, that.artistId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(profileId, artistId);
	}
}
