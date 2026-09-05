package com.groove.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.MemberFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.dto.AddressCreateRequest;
import com.groove.member.dto.AddressResponse;
import com.groove.member.dto.AddressUpdateRequest;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ADDRESS_ID = 10L;

	@Mock
	AddressRepository addressRepository;

	@Mock
	MemberRepository memberRepository;

	AddressService addressService;

	Member member;

	@BeforeEach
	void setUp() {
		addressService = new AddressService(addressRepository, memberRepository);
		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
	}

	@Nested
	@DisplayName("getAddresses()")
	class GetAddresses {

		@Test
		@DisplayName("기본 배송지 우선 정렬된 목록을 반환한다")
		void returnsAddressesOrderedByDefaultFirst() {
			// given
			Address defaultAddress = AddressFixture.withId(AddressFixture.createDefault(member), ADDRESS_ID);
			Address other = AddressFixture.withId(AddressFixture.create(member), 11L);
			given(addressRepository.findAllByMemberIdOrderByIsDefaultDescIdAsc(MEMBER_ID))
					.willReturn(List.of(defaultAddress, other));

			// when
			List<AddressResponse> responses = addressService.getAddresses(MEMBER_ID);

			// then
			assertThat(responses).hasSize(2);
			assertThat(responses.get(0).id()).isEqualTo(ADDRESS_ID);
			assertThat(responses.get(0).isDefault()).isTrue();
			assertThat(responses.get(1).isDefault()).isFalse();
		}
	}

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("첫 배송지면 isDefault 요청값과 무관하게 기본으로 등록한다")
		void marksFirstAddressAsDefaultRegardlessOfRequest() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.countByMemberId(MEMBER_ID)).willReturn(0L);
			given(addressRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
			AddressCreateRequest request = AddressFixture.createRequest(false);

			// when
			AddressResponse response = addressService.create(MEMBER_ID, request);

			// then
			assertThat(response.isDefault()).isTrue();
		}

		@Test
		@DisplayName("isDefault 가 true 면 기존 기본 배송지를 해제한다")
		void demotesExistingDefaultWhenRequestedAsDefault() {
			// given
			Address existingDefault = AddressFixture.createDefault(member);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.countByMemberId(MEMBER_ID)).willReturn(1L);
			given(addressRepository.findDefaultByMemberId(MEMBER_ID)).willReturn(Optional.of(existingDefault));
			given(addressRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
			AddressCreateRequest request = AddressFixture.createRequest(true);

			// when
			AddressResponse response = addressService.create(MEMBER_ID, request);

			// then
			assertThat(existingDefault.isDefault()).isFalse();
			assertThat(response.isDefault()).isTrue();
		}

		@Test
		@DisplayName("isDefault 가 false 이고 기존 배송지가 있으면 기본으로 등록하지 않는다")
		void doesNotMarkAsDefaultWhenNotRequested() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.countByMemberId(MEMBER_ID)).willReturn(1L);
			given(addressRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
			AddressCreateRequest request = AddressFixture.createRequest(false);

			// when
			AddressResponse response = addressService.create(MEMBER_ID, request);

			// then
			assertThat(response.isDefault()).isFalse();
			verify(addressRepository, never()).findDefaultByMemberId(any());
		}

		@Test
		@DisplayName("이미 10개 등록되어 있으면 MEMBER_ADDRESS_LIMIT_EXCEEDED 예외를 던진다")
		void throwsWhenAddressLimitExceeded() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.countByMemberId(MEMBER_ID)).willReturn((long) AddressService.MAX_ADDRESS_COUNT);
			AddressCreateRequest request = AddressFixture.createRequest();

			// when & then
			assertThatThrownBy(() -> addressService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_ADDRESS_LIMIT_EXCEEDED);
			verify(addressRepository, never()).save(any());
		}

		@Test
		@DisplayName("회원이 존재하지 않으면 MEMBER_NOT_FOUND 예외를 던진다")
		void throwsWhenMemberNotFound() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());
			AddressCreateRequest request = AddressFixture.createRequest();

			// when & then
			assertThatThrownBy(() -> addressService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenMemberWithdrawn() {
			// given
			Member withdrawn = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));
			AddressCreateRequest request = AddressFixture.createRequest();

			// when & then
			assertThatThrownBy(() -> addressService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
		}

		@Test
		@DisplayName("정지된 회원이면 AUTH_MEMBER_SUSPENDED 예외를 던진다")
		void throwsWhenMemberSuspended() {
			// given
			Member suspended = MemberFixture.withId(MemberFixture.createSuspended(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(suspended));
			AddressCreateRequest request = AddressFixture.createRequest();

			// when & then
			assertThatThrownBy(() -> addressService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_MEMBER_SUSPENDED);
		}
	}

	@Nested
	@DisplayName("update()")
	class Update {

		@Test
		@DisplayName("본인 배송지면 필드를 수정하고 응답을 반환한다")
		void updatesFieldsAndReturnsResponse() {
			// given
			Address address = AddressFixture.withId(AddressFixture.create(member), ADDRESS_ID);
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			AddressUpdateRequest request = AddressFixture.updateRequest();

			// when
			AddressResponse response = addressService.update(MEMBER_ID, ADDRESS_ID, request);

			// then
			assertThat(address.getRecipientName()).isEqualTo(request.recipientName());
			assertThat(response.recipientName()).isEqualTo(request.recipientName());
		}

		@Test
		@DisplayName("타인 소유이거나 없는 id 면 MEMBER_ADDRESS_NOT_FOUND 예외를 던진다")
		void throwsWhenNotOwned() {
			// given
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.empty());
			AddressUpdateRequest request = AddressFixture.updateRequest();

			// when & then
			assertThatThrownBy(() -> addressService.update(MEMBER_ID, ADDRESS_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_ADDRESS_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("delete()")
	class Delete {

		@Test
		@DisplayName("기본 배송지가 아니면 삭제만 하고 승계 대상을 조회하지 않는다")
		void deletesWithoutPromotionWhenNotDefault() {
			// given
			Address address = AddressFixture.withId(AddressFixture.create(member), ADDRESS_ID);
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));

			// when
			addressService.delete(MEMBER_ID, ADDRESS_ID);

			// then
			verify(addressRepository).delete(address);
			verify(addressRepository, never()).findAllByMemberIdOrderByIdAsc(any());
		}

		@Test
		@DisplayName("기본 배송지를 삭제하면 남은 배송지 중 id 가 가장 작은 배송지를 기본으로 승계한다")
		void promotesOldestRemainingWhenDefaultDeleted() {
			// given
			Address address = AddressFixture.withId(AddressFixture.createDefault(member), ADDRESS_ID);
			Address remaining = AddressFixture.withId(AddressFixture.create(member), 11L);
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(addressRepository.findAllByMemberIdOrderByIdAsc(MEMBER_ID)).willReturn(List.of(remaining));

			// when
			addressService.delete(MEMBER_ID, ADDRESS_ID);

			// then
			verify(addressRepository).delete(address);
			verify(addressRepository).flush();
			assertThat(remaining.isDefault()).isTrue();
		}

		@Test
		@DisplayName("기본 배송지를 삭제했는데 남은 배송지가 없으면 아무 일도 일어나지 않는다")
		void doesNothingWhenNoAddressRemains() {
			// given
			Address address = AddressFixture.withId(AddressFixture.createDefault(member), ADDRESS_ID);
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(addressRepository.findAllByMemberIdOrderByIdAsc(MEMBER_ID)).willReturn(List.of());

			// when & then
			addressService.delete(MEMBER_ID, ADDRESS_ID);
			verify(addressRepository).delete(address);
		}
	}

	@Nested
	@DisplayName("setDefault()")
	class SetDefault {

		@Test
		@DisplayName("기존 기본 배송지를 해제하고 대상을 기본으로 지정한다")
		void demotesOldAndMarksNewAsDefault() {
			// given
			Address oldDefault = AddressFixture.withId(AddressFixture.createDefault(member), 11L);
			Address target = AddressFixture.withId(AddressFixture.create(member), ADDRESS_ID);
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(target));
			given(addressRepository.findDefaultByMemberId(MEMBER_ID)).willReturn(Optional.of(oldDefault));

			// when
			AddressResponse response = addressService.setDefault(MEMBER_ID, ADDRESS_ID);

			// then
			assertThat(oldDefault.isDefault()).isFalse();
			assertThat(target.isDefault()).isTrue();
			assertThat(response.isDefault()).isTrue();
		}

		@Test
		@DisplayName("이미 기본 배송지면 기본 배송지 조회 없이 그대로 반환한다")
		void skipsLookupWhenAlreadyDefault() {
			// given
			Address target = AddressFixture.withId(AddressFixture.createDefault(member), ADDRESS_ID);
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(target));

			// when
			AddressResponse response = addressService.setDefault(MEMBER_ID, ADDRESS_ID);

			// then
			assertThat(response.isDefault()).isTrue();
			verify(addressRepository, never()).findDefaultByMemberId(any());
		}

		@Test
		@DisplayName("본인 소유가 아니면 MEMBER_ADDRESS_NOT_FOUND 예외를 던진다")
		void throwsWhenNotOwned() {
			// given
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> addressService.setDefault(MEMBER_ID, ADDRESS_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_ADDRESS_NOT_FOUND);
		}
	}
}
