package com.groove.recommend.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import com.groove.global.common.BaseTimeEntity;
import com.groove.member.entity.Member;

import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원 취향 프로필. 회원당 하나만 존재한다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "member_taste_profile",
		uniqueConstraints = @UniqueConstraint(name = "uk_taste_profile_member", columnNames = "member_id"))
public class MemberTasteProfile extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_taste_profile_member"))
	private Member member;

	@Builder(access = PRIVATE)
	private MemberTasteProfile(Member member) {
		this.member = member;
	}

	public static MemberTasteProfile create(Member member) {
		return MemberTasteProfile.builder()
				.member(member)
				.build();
	}
}
