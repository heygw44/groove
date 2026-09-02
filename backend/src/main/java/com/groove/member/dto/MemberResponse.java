package com.groove.member.dto;

import java.time.LocalDateTime;

import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;

public record MemberResponse(
		Long id,
		String email,
		String nickname,
		MemberRole role,
		MemberStatus status,
		LocalDateTime createdAt
) {

	public static MemberResponse from(Member member) {
		return new MemberResponse(member.getId(), member.getEmail(), member.getNickname(), member.getRole(),
				member.getStatus(), member.getCreatedAt());
	}
}
