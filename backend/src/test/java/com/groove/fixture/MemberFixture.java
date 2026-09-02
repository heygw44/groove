package com.groove.fixture;

import com.groove.member.entity.Member;

public final class MemberFixture {

	private static final String ENCODED_PASSWORD = "$2a$10$encodedpassword";
	private static final String NICKNAME = "그루버";

	private MemberFixture() {
	}

	public static Member create() {
		return create("groover@groove.com");
	}

	public static Member create(String email) {
		return Member.create(email, ENCODED_PASSWORD, NICKNAME);
	}
}
