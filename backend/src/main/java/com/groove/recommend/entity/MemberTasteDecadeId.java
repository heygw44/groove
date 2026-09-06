package com.groove.recommend.entity;

import static lombok.AccessLevel.PROTECTED;

import java.io.Serializable;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@link MemberTasteDecade} 복합 키. */
@Embeddable
@Getter
@NoArgsConstructor(access = PROTECTED)
public class MemberTasteDecadeId implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long profileId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 5)
	private Decade decade;

	private MemberTasteDecadeId(Long profileId, Decade decade) {
		this.profileId = profileId;
		this.decade = decade;
	}

	public static MemberTasteDecadeId of(Long profileId, Decade decade) {
		return new MemberTasteDecadeId(profileId, decade);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MemberTasteDecadeId that)) {
			return false;
		}
		return Objects.equals(profileId, that.profileId)
				&& decade == that.decade;
	}

	@Override
	public int hashCode() {
		return Objects.hash(profileId, decade);
	}
}
