package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.member.entity.Member;
import com.groove.recommend.entity.MemberTasteProfile;

public final class TasteProfileFixture {

	private TasteProfileFixture() {
	}

	public static MemberTasteProfile create(Member member) {
		return MemberTasteProfile.create(member);
	}

	public static MemberTasteProfile withId(MemberTasteProfile profile, Long id) {
		ReflectionTestUtils.setField(profile, "id", id);
		return profile;
	}
}
