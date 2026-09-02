package com.groove.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.MemberFixture;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.support.DataJpaTestSupport;

class AddressRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("저장하면 member 와 연관되어 저장된다")
		void persistsWithMember() {
			// given
			Member member = memberRepository.save(MemberFixture.create("addr-save@groove.com"));
			Address address = AddressFixture.create(member);

			// when
			Address saved = addressRepository.save(address);

			// then
			assertThat(saved.getId()).isNotNull();
			assertThat(saved.getMember().getId()).isEqualTo(member.getId());
		}
	}

	@Nested
	@DisplayName("findAllByMemberIdOrderByIdAsc()")
	class FindAllByMemberIdOrderByIdAsc {

		@Test
		@DisplayName("여러 건이면 id 오름차순으로 반환한다")
		void returnsInAscendingIdOrder() {
			// given
			Member member = memberRepository.save(MemberFixture.create("addr-list@groove.com"));
			Address first = addressRepository.save(AddressFixture.create(member));
			Address second = addressRepository.save(AddressFixture.create(member));
			Address third = addressRepository.save(AddressFixture.create(member));

			// when
			List<Address> addresses = addressRepository.findAllByMemberIdOrderByIdAsc(member.getId());

			// then
			assertThat(addresses).extracting(Address::getId)
					.containsExactly(first.getId(), second.getId(), third.getId());
		}
	}

	@Nested
	@DisplayName("findByIdAndMemberId()")
	class FindByIdAndMemberId {

		@Test
		@DisplayName("다른 회원의 id 로 조회하면 empty 를 반환한다")
		void returnsEmptyForOtherMember() {
			// given
			Member owner = memberRepository.save(MemberFixture.create("addr-owner@groove.com"));
			Member other = memberRepository.save(MemberFixture.create("addr-other@groove.com"));
			Address address = addressRepository.save(AddressFixture.create(owner));

			// when
			Optional<Address> found = addressRepository.findByIdAndMemberId(address.getId(), other.getId());

			// then
			assertThat(found).isEmpty();
		}
	}

	@Nested
	@DisplayName("findDefaultByMemberId()")
	class FindDefaultByMemberId {

		@Test
		@DisplayName("기본 배송지가 있으면 반환한다")
		void returnsDefaultAddress() {
			// given
			Member member = memberRepository.save(MemberFixture.create("addr-default@groove.com"));
			addressRepository.save(AddressFixture.create(member));
			Address defaultAddress = addressRepository.save(AddressFixture.createDefault(member));

			// when
			Optional<Address> found = addressRepository.findDefaultByMemberId(member.getId());

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getId()).isEqualTo(defaultAddress.getId());
		}
	}

	@Nested
	@DisplayName("countByMemberId()")
	class CountByMemberId {

		@Test
		@DisplayName("3건 저장되어 있으면 3을 반환한다")
		void returnsCount() {
			// given
			Member member = memberRepository.save(MemberFixture.create("addr-count@groove.com"));
			addressRepository.save(AddressFixture.create(member));
			addressRepository.save(AddressFixture.create(member));
			addressRepository.save(AddressFixture.create(member));

			// when
			long count = addressRepository.countByMemberId(member.getId());

			// then
			assertThat(count).isEqualTo(3);
		}
	}
}
