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
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 장르. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "genre", uniqueConstraints = @UniqueConstraint(name = "uk_genre_name", columnNames = "name"))
public class Genre extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	@Builder(access = PRIVATE)
	private Genre(String name) {
		this.name = name;
	}

	public static Genre create(String name) {
		return Genre.builder()
				.name(name)
				.build();
	}
}
