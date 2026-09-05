package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

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

	public static Member create(String email, String nickname) {
		return Member.create(email, ENCODED_PASSWORD, nickname);
	}

	public static Member createAdmin() {
		return createAdmin("admin@groove.com");
	}

	public static Member createAdmin(String email) {
		return Member.createAdmin(email, ENCODED_PASSWORD, "관리자");
	}

	public static Member createWithdrawn() {
		Member member = create();
		member.withdraw();
		return member;
	}

	public static Member createSuspended() {
		Member member = create();
		member.suspend();
		return member;
	}

	public static Member createSuspended(String email) {
		Member member = create(email);
		member.suspend();
		return member;
	}

	public static Member withId(Member member, Long id) {
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	public static String encodedPassword() {
		return ENCODED_PASSWORD;
	}
}
