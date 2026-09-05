package com.groove.product.entity;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import com.groove.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 음반 레이블. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "label")
public class Label extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 50)
	private String country;

	@Builder(access = PRIVATE)
	private Label(String name, String country) {
		this.name = name;
		this.country = country;
	}

	public static Label create(String name, String country) {
		return Label.builder()
				.name(name)
				.country(country)
				.build();
	}
}
