package com.groove.order.entity;

import static lombok.AccessLevel.PROTECTED;

import java.util.Objects;

import com.groove.member.entity.Address;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주문 시점 배송지 스냅샷. */
@Embeddable
@Getter
@NoArgsConstructor(access = PROTECTED)
public class ShippingAddress {

	@Column(name = "recipient_name", nullable = false, length = 30)
	private String recipientName;

	@Column(nullable = false, length = 20)
	private String phone;

	@Column(name = "zip_code", nullable = false, length = 10)
	private String zipCode;

	@Column(nullable = false, length = 200)
	private String address1;

	@Column(length = 200)
	private String address2;

	private ShippingAddress(String recipientName, String phone, String zipCode, String address1, String address2) {
		this.recipientName = recipientName;
		this.phone = phone;
		this.zipCode = zipCode;
		this.address1 = address1;
		this.address2 = address2;
	}

	public static ShippingAddress of(String recipientName, String phone, String zipCode, String address1,
			String address2) {
		return new ShippingAddress(recipientName, phone, zipCode, address1, address2);
	}

	public static ShippingAddress from(Address address) {
		return new ShippingAddress(address.getRecipientName(), address.getPhone(), address.getZipCode(),
				address.getAddress1(), address.getAddress2());
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ShippingAddress that)) {
			return false;
		}
		return Objects.equals(recipientName, that.recipientName)
				&& Objects.equals(phone, that.phone)
				&& Objects.equals(zipCode, that.zipCode)
				&& Objects.equals(address1, that.address1)
				&& Objects.equals(address2, that.address2);
	}

	@Override
	public int hashCode() {
		return Objects.hash(recipientName, phone, zipCode, address1, address2);
	}
}
