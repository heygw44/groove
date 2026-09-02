package com.groove.member.dto;

import com.groove.member.entity.Address;

public record AddressResponse(
		Long id,
		String recipientName,
		String phone,
		String zipCode,
		String address1,
		String address2,
		boolean isDefault
) {

	public static AddressResponse from(Address address) {
		return new AddressResponse(address.getId(), address.getRecipientName(), address.getPhone(),
				address.getZipCode(), address.getAddress1(), address.getAddress2(), address.isDefault());
	}
}
