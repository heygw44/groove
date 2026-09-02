package com.groove.fixture;

import com.groove.member.entity.Address;
import com.groove.member.entity.Member;

public final class AddressFixture {

	private AddressFixture() {
	}

	public static Address create(Member member) {
		return Address.create(member, "김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101동 1001호", false);
	}

	public static Address createDefault(Member member) {
		return Address.create(member, "김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101동 1001호", true);
	}
}
