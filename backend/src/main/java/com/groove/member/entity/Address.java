package com.groove.member.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import org.hibernate.annotations.ColumnDefault;

import com.groove.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 배송지. 회원당 기본배송지 1개 보장은 서비스 계층 책임(엔티티는 강제하지 않는다). */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "address")
public class Address extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_address_member"))
	private Member member;

	@Column(nullable = false, length = 30)
	private String recipientName;

	@Column(nullable = false, length = 20)
	private String phone;

	@Column(name = "zip_code", nullable = false, length = 10)
	private String zipCode;

	@Column(nullable = false, length = 200)
	private String address1;

	@Column(length = 200)
	private String address2;

	@Column(name = "is_default", nullable = false)
	@ColumnDefault("false")
	private boolean isDefault;

	@Builder(access = PRIVATE)
	private Address(Member member, String recipientName, String phone, String zipCode, String address1,
			String address2, boolean isDefault) {
		this.member = member;
		this.recipientName = recipientName;
		this.phone = phone;
		this.zipCode = zipCode;
		this.address1 = address1;
		this.address2 = address2;
		this.isDefault = isDefault;
	}

	public static Address create(Member member, String recipientName, String phone, String zipCode,
			String address1, String address2, boolean isDefault) {
		return Address.builder()
				.member(member)
				.recipientName(recipientName)
				.phone(phone)
				.zipCode(zipCode)
				.address1(address1)
				.address2(address2)
				.isDefault(isDefault)
				.build();
	}

	public void update(String recipientName, String phone, String zipCode, String address1, String address2) {
		this.recipientName = recipientName;
		this.phone = phone;
		this.zipCode = zipCode;
		this.address1 = address1;
		this.address2 = address2;
	}

	public void markAsDefault() {
		this.isDefault = true;
	}

	public void unmarkDefault() {
		this.isDefault = false;
	}
}
