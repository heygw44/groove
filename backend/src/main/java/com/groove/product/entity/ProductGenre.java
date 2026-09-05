package com.groove.product.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

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

/** 상품-장르 매핑. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "product_genre",
		uniqueConstraints = @UniqueConstraint(name = "uk_product_genre", columnNames = {"product_id", "genre_id"}),
		indexes = @Index(name = "idx_product_genre_genre", columnList = "genre_id"))
public class ProductGenre {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pg_product"))
	private Product product;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "genre_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pg_genre"))
	private Genre genre;

	@Builder(access = PRIVATE)
	private ProductGenre(Product product, Genre genre) {
		this.product = product;
		this.genre = genre;
	}

	public static ProductGenre of(Product product, Genre genre) {
		return ProductGenre.builder()
				.product(product)
				.genre(genre)
				.build();
	}
}
