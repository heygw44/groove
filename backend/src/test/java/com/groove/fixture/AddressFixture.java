package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.member.dto.AddressCreateRequest;
import com.groove.member.dto.AddressUpdateRequest;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;

public final class AddressFixture {

	private AddressFixture() {
	}

	public static Address create(Member member) {
		return Address.create(member, "김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101동 1001호", false);
	}

	public static Address create(Member member, String recipientName) {
		return Address.create(member, recipientName, "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101동 1001호", false);
	}

	public static Address createDefault(Member member) {
		return Address.create(member, "김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101동 1001호", true);
	}

	public static Address withId(Address address, Long id) {
		ReflectionTestUtils.setField(address, "id", id);
		return address;
	}

	public static AddressCreateRequest createRequest() {
		return createRequest(null);
	}

	public static AddressCreateRequest createRequest(Boolean isDefault) {
		return new AddressCreateRequest("김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101동 1001호",
				isDefault);
	}

	public static AddressUpdateRequest updateRequest() {
		return new AddressUpdateRequest("새그루브", "010-9876-5432", "04524", "서울시 중구 세종대로 1", "202동 2002호");
	}
}
