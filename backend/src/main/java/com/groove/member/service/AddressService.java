package com.groove.member.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.dto.AddressCreateRequest;
import com.groove.member.dto.AddressResponse;
import com.groove.member.dto.AddressUpdateRequest;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** 배송지 CRUD. 회원당 기본 배송지가 항상 0~1개임을 이 계층에서 보장한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AddressService {

	public static final int MAX_ADDRESS_COUNT = 10;

	private final AddressRepository addressRepository;
	private final MemberRepository memberRepository;

	public List<AddressResponse> getAddresses(Long memberId) {
		return addressRepository.findAllByMemberIdOrderByIsDefaultDescIdAsc(memberId).stream()
				.map(AddressResponse::from)
				.toList();
	}

	@Transactional
	public AddressResponse create(Long memberId, AddressCreateRequest request) {
		Member member = findActiveMember(memberId);
		long count = addressRepository.countByMemberId(memberId);
		if (count >= MAX_ADDRESS_COUNT) {
			throw new BusinessException(ErrorCode.MEMBER_ADDRESS_LIMIT_EXCEEDED);
		}

		boolean makeDefault = count == 0 || Boolean.TRUE.equals(request.isDefault());
		if (makeDefault) {
			addressRepository.findDefaultByMemberId(memberId).ifPresent(Address::unmarkDefault);
		}

		Address address = Address.create(member, request.recipientName(), request.phone(), request.zipCode(),
				request.address1(), request.address2(), makeDefault);
		Address saved = addressRepository.save(address);
		return AddressResponse.from(saved);
	}

	@Transactional
	public AddressResponse update(Long memberId, Long addressId, AddressUpdateRequest request) {
		Address address = findOwned(memberId, addressId);
		address.update(request.recipientName(), request.phone(), request.zipCode(), request.address1(),
				request.address2());
		return AddressResponse.from(address);
	}

	@Transactional
	public void delete(Long memberId, Long addressId) {
		Address address = findOwned(memberId, addressId);
		boolean wasDefault = address.isDefault();
		addressRepository.delete(address);

		if (wasDefault) {
			// 삭제로 flush 되어야 남은 배송지 목록에서 방금 지운 항목이 빠진다.
			addressRepository.flush();
			addressRepository.findAllByMemberIdOrderByIdAsc(memberId).stream()
					.findFirst()
					.ifPresent(Address::markAsDefault);
		}
	}

	@Transactional
	public AddressResponse setDefault(Long memberId, Long addressId) {
		Address address = findOwned(memberId, addressId);
		if (address.isDefault()) {
			return AddressResponse.from(address);
		}

		addressRepository.findDefaultByMemberId(memberId).ifPresent(Address::unmarkDefault);
		address.markAsDefault();
		return AddressResponse.from(address);
	}

	private Member findActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		member.validateActive();
		return member;
	}

	private Address findOwned(Long memberId, Long addressId) {
		return addressRepository.findByIdAndMemberId(addressId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_ADDRESS_NOT_FOUND));
	}
}
