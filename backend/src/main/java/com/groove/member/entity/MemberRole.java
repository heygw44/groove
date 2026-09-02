package com.groove.member.entity;

/** 회원 권한. Security 권한 문자열은 {@code ROLE_} 접두사를 붙여 사용한다. */
public enum MemberRole {

	USER, ADMIN;

	public String authority() {
		return "ROLE_" + name();
	}
}
