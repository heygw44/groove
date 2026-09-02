package com.groove.product.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "product",
		indexes = {
			@Index(name = "idx_product_title_artist", columnList = "title, artist_id"),
			@Index(name = "idx_product_status_created", columnList = "status, created_at"),
			@Index(name = "idx_product_artist", columnList = "artist_id"),
			@Index(name = "idx_product_label", columnList = "label_id")
		})
public class Product extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "artist_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_artist"))
	private Artist artist;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "label_id", foreignKey = @ForeignKey(name = "fk_product_label"))
	private Label label;

	@Column(name = "release_date")
	private LocalDate releaseDate;

	@Column(name = "pressing_info", length = 100)
	private String pressingInfo;

	@Column(name = "color_variant", length = 50)
	private String colorVariant;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	@ColumnDefault("'ON_SALE'")
	private ProductStatus status;

	@Column(columnDefinition = "TEXT")
	private String description;

	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ProductGenre> productGenres = new ArrayList<>();

	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder ASC")
	private List<ProductImage> images = new ArrayList<>();

	@Builder(access = PRIVATE)
	private Product(String title, Artist artist, Label label, LocalDate releaseDate, String pressingInfo,
			String colorVariant, BigDecimal price, ProductStatus status, String description) {
		this.title = title;
		this.artist = artist;
		this.label = label;
		this.releaseDate = releaseDate;
		this.pressingInfo = pressingInfo;
		this.colorVariant = colorVariant;
		this.price = price;
		this.status = status;
		this.description = description;
	}

	public static Product create(String title, Artist artist, Label label, LocalDate releaseDate,
			String pressingInfo, String colorVariant, BigDecimal price, String description) {
		return Product.builder()
				.title(title)
				.artist(artist)
				.label(label)
				.releaseDate(releaseDate)
				.pressingInfo(pressingInfo)
				.colorVariant(colorVariant)
				.price(price)
				.status(ProductStatus.ON_SALE)
				.description(description)
				.build();
	}

	public void hide() {
		this.status = ProductStatus.HIDDEN;
	}

	public void markSoldOut() {
		this.status = ProductStatus.SOLD_OUT;
	}

	public void resume() {
		this.status = ProductStatus.ON_SALE;
	}

	public boolean isHidden() {
		return this.status == ProductStatus.HIDDEN;
	}

	public boolean isOnSale() {
		return this.status == ProductStatus.ON_SALE;
	}

	public void updateInfo(String title, Artist artist, Label label, LocalDate releaseDate, String pressingInfo,
			String colorVariant, BigDecimal price, String description) {
		this.title = title;
		this.artist = artist;
		this.label = label;
		this.releaseDate = releaseDate;
		this.pressingInfo = pressingInfo;
		this.colorVariant = colorVariant;
		this.price = price;
		this.description = description;
	}

	public void addGenre(Genre genre) {
		boolean alreadyLinked = this.productGenres.stream()
				.map(ProductGenre::getGenre)
				.anyMatch(linked -> linked == genre
						|| (genre.getId() != null && genre.getId().equals(linked.getId())));
		if (alreadyLinked) {
			return;
		}
		this.productGenres.add(ProductGenre.of(this, genre));
	}

	public void replaceGenres(List<Genre> genres) {
		this.productGenres.clear();
		genres.forEach(this::addGenre);
	}

	public void addImage(String imageUrl, int sortOrder) {
		this.images.add(ProductImage.of(this, imageUrl, sortOrder));
	}

	public void clearImages() {
		this.images.clear();
	}
}
