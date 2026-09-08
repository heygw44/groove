package com.groove.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;

class ShippingAddressTest {

	private static Stream<Arguments> differingFields() {
		return Stream.of(
				Arguments.of("수령인",
						ShippingAddress.of("다른이름", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101동 1001호")),
				Arguments.of("전화번호",
						ShippingAddress.of("김그루브", "010-9999-9999", "06236", "서울시 강남구 테헤란로 1", "101동 1001호")),
				Arguments.of("우편번호",
						ShippingAddress.of("김그루브", "010-1234-5678", "99999", "서울시 강남구 테헤란로 1", "101동 1001호")),
				Arguments.of("주소1",
						ShippingAddress.of("김그루브", "010-1234-5678", "06236", "다른 주소", "101동 1001호")),
				Arguments.of("주소2",
						ShippingAddress.of("김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "다른 상세주소"))
		);
	}

	@Nested
	@DisplayName("equals()")
	class Equals {

		@Test
		@DisplayName("같은 인스턴스면 같다")
		void returnsTrueForSameInstance() {
			// given
			ShippingAddress shippingAddress = OrderFixture.shippingAddress();

			// when & then
			assertThat(shippingAddress).isEqualTo(shippingAddress);
		}

		@Test
		@DisplayName("null 과 다르다")
		void returnsFalseWhenComparedWithNull() {
			// given
			ShippingAddress shippingAddress = OrderFixture.shippingAddress();

			// when & then
			assertThat(shippingAddress).isNotEqualTo(null);
		}

		@Test
		@DisplayName("다른 타입과 다르다")
		void returnsFalseWhenComparedWithDifferentType() {
			// given
			ShippingAddress shippingAddress = OrderFixture.shippingAddress();

			// when & then
			assertThat(shippingAddress).isNotEqualTo("김그루브");
		}

		@Test
		@DisplayName("다섯 필드가 모두 같으면 같다")
		void returnsTrueWhenAllFieldsEqual() {
			// given
			ShippingAddress shippingAddress = OrderFixture.shippingAddress();
			ShippingAddress other = ShippingAddress.of("김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1",
					"101동 1001호");

			// when & then
			assertThat(shippingAddress).isEqualTo(other);
		}

		@ParameterizedTest(name = "{0}이 다르면 다르다")
		@MethodSource("com.groove.order.entity.ShippingAddressTest#differingFields")
		@DisplayName("필드가 하나라도 다르면 다르다")
		void returnsFalseWhenFieldDiffers(String fieldName, ShippingAddress other) {
			// given
			ShippingAddress shippingAddress = OrderFixture.shippingAddress();

			// when & then
			assertThat(shippingAddress).isNotEqualTo(other);
		}

		@Test
		@DisplayName("Address 로부터 만든 배송지도 필드가 같으면 같다")
		void returnsTrueWhenCreatedFromEqualAddress() {
			// given
			Member member = MemberFixture.create();
			Address address = AddressFixture.create(member);

			// when
			ShippingAddress shippingAddress = ShippingAddress.from(address);

			// then
			assertThat(shippingAddress).isEqualTo(OrderFixture.shippingAddress());
		}
	}

	@Nested
	@DisplayName("hashCode()")
	class HashCode {

		@Test
		@DisplayName("다섯 필드가 모두 같으면 해시코드도 같다")
		void returnsSameHashCodeWhenAllFieldsEqual() {
			// given
			ShippingAddress shippingAddress = OrderFixture.shippingAddress();
			ShippingAddress other = ShippingAddress.of("김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1",
					"101동 1001호");

			// when & then
			assertThat(shippingAddress).hasSameHashCodeAs(other);
		}
	}
}
