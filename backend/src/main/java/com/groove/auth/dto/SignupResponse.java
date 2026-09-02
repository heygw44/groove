package com.groove.auth.dto;

import com.groove.member.entity.Member;

public record SignupResponse(Long id, String email, String nickname) {

	public static SignupResponse from(Member member) {
		return new SignupResponse(member.getId(), member.getEmail(), member.getNickname());
	}
}
