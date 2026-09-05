package com.groove.recommend.entity;

import static lombok.AccessLevel.PROTECTED;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@link MemberTasteGenre} 복합 키. */
@Embeddable
@Getter
@NoArgsConstructor(access = PROTECTED)
public class MemberTasteGenreId implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long profileId;

	private Long genreId;

	private MemberTasteGenreId(Long profileId, Long genreId) {
		this.profileId = profileId;
		this.genreId = genreId;
	}

	public static MemberTasteGenreId of(Long profileId, Long genreId) {
		return new MemberTasteGenreId(profileId, genreId);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MemberTasteGenreId that)) {
			return false;
		}
		return Objects.equals(profileId, that.profileId)
				&& Objects.equals(genreId, that.genreId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(profileId, genreId);
	}
}
