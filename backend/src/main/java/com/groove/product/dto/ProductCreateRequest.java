package com.groove.product.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
		@NotBlank(message = "제목은 필수입니다.")
		@Size(max = 200, message = "제목은 200자 이하여야 합니다.")
		String title,

		@NotNull(message = "아티스트는 필수입니다.")
		Long artistId,

		Long labelId,

		List<Long> genreIds,

		LocalDate releaseDate,

		@Size(max = 100, message = "프레싱 정보는 100자 이하여야 합니다.")
		String pressingInfo,

		@Size(max = 50, message = "컬러 배리언트는 50자 이하여야 합니다.")
		String colorVariant,

		@NotNull(message = "가격은 필수입니다.")
		@DecimalMin(value = "0", message = "가격은 0 이상이어야 합니다.")
		@Digits(integer = 8, fraction = 2, message = "가격 형식이 올바르지 않습니다.")
		BigDecimal price,

		String description,

		List<@NotBlank(message = "이미지 URL은 비어 있을 수 없습니다.")
		@Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.") String> imageUrls,

		@NotNull(message = "초기 재고 수량은 필수입니다.")
		@PositiveOrZero(message = "초기 재고 수량은 0 이상이어야 합니다.")
		Integer initialStock
) {
}
