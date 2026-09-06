package com.groove.recommend.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 취향 프로필의 선호 연대. 프로필이 먼저 저장돼 id 를 가진 뒤에 만든다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "member_taste_decade")
public class MemberTasteDecade {

	@EmbeddedId
	private MemberTasteDecadeId id;

	@MapsId("profileId")
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "profile_id", foreignKey = @ForeignKey(name = "fk_taste_decade_profile"))
	private MemberTasteProfile profile;

	private MemberTasteDecade(MemberTasteDecadeId id, MemberTasteProfile profile) {
		this.id = id;
		this.profile = profile;
	}

	public static MemberTasteDecade of(MemberTasteProfile profile, Decade decade) {
		MemberTasteDecadeId id = MemberTasteDecadeId.of(profile.getId(), decade);
		return new MemberTasteDecade(id, profile);
	}

	public Decade getDecade() {
		return id.getDecade();
	}
}
