package com.groove.product.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import com.groove.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 앨범(작품 단위). 국가·연도·에디션이 다른 프레싱(product)을 여러 개 가질 수 있다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "album",
		uniqueConstraints = @UniqueConstraint(name = "uk_album_discogs_master", columnNames = "discogs_master_id"),
		indexes = {
			@Index(name = "idx_album_artist", columnList = "artist_id"),
			@Index(name = "idx_album_title_artist", columnList = "title, artist_id")
		})
public class Album extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "artist_id", nullable = false, foreignKey = @ForeignKey(name = "fk_album_artist"))
	private Artist artist;

	@Column(name = "original_release_year")
	private Integer originalReleaseYear;

	@Column(name = "discogs_master_id")
	private Long discogsMasterId;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Builder(access = PRIVATE)
	private Album(String title, Artist artist, Integer originalReleaseYear, String description) {
		this.title = title;
		this.artist = artist;
		this.originalReleaseYear = originalReleaseYear;
		this.description = description;
	}

	public static Album create(String title, Artist artist, Integer originalReleaseYear) {
		return Album.builder()
				.title(title)
				.artist(artist)
				.originalReleaseYear(originalReleaseYear)
				.build();
	}

	public void linkDiscogsMaster(Long discogsMasterId) {
		this.discogsMasterId = discogsMasterId;
	}
}
