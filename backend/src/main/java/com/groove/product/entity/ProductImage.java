package com.groove.product.entity;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품 이미지. sort_order 0 이 대표 이미지 규약. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "product_image",
		indexes = @Index(name = "idx_product_image_product", columnList = "product_id, sort_order"))
public class ProductImage extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_image_product"))
	private Product product;

	@Column(name = "image_url", nullable = false, length = 500)
	private String imageUrl;

	@Column(name = "sort_order", nullable = false)
	@ColumnDefault("0")
	private int sortOrder;

	@Builder(access = PRIVATE)
	private ProductImage(Product product, String imageUrl, int sortOrder) {
		this.product = product;
		this.imageUrl = imageUrl;
		this.sortOrder = sortOrder;
	}

	public static ProductImage of(Product product, String imageUrl, int sortOrder) {
		return ProductImage.builder()
				.product(product)
				.imageUrl(imageUrl)
				.sortOrder(sortOrder)
				.build();
	}

	public boolean isThumbnail() {
		return this.sortOrder == 0;
	}

	public void changeSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}
}
