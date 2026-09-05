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

/** 아티스트. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "artist")
public class Artist extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "name_en", length = 100)
	private String nameEn;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Builder(access = PRIVATE)
	private Artist(String name, String nameEn, String description) {
		this.name = name;
		this.nameEn = nameEn;
		this.description = description;
	}

	public static Artist create(String name, String nameEn, String description) {
		return Artist.builder()
				.name(name)
				.nameEn(nameEn)
				.description(description)
				.build();
	}
}
