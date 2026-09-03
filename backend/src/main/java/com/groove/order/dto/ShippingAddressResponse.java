package com.groove.order.dto;

import com.groove.order.entity.ShippingAddress;

public record ShippingAddressResponse(
		String recipientName,
		String phone,
		String zipCode,
		String address1,
		String address2
) {

	public static ShippingAddressResponse from(ShippingAddress shippingAddress) {
		return new ShippingAddressResponse(shippingAddress.getRecipientName(), shippingAddress.getPhone(),
				shippingAddress.getZipCode(), shippingAddress.getAddress1(), shippingAddress.getAddress2());
	}
}
