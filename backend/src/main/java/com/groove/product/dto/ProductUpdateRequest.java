package com.groove.product.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.openapitools.jackson.nullable.JsonNullable;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 부분 수정 요청. 필드가 null 이면 기존 값을 유지한다. genreIds/imageUrls 는 null=유지, 빈 리스트=전부 제거.
 * labelId 는 키 생략=유지, null=해제, 값=교체.
 */
public record ProductUpdateRequest(
		@Size(max = 200, message = "제목은 200자 이하여야 합니다.")
		String title,

		Long artistId,

		JsonNullable<Long> labelId,

		List<Long> genreIds,

		LocalDate releaseDate,

		@Size(max = 100, message = "프레싱 정보는 100자 이하여야 합니다.")
		String pressingInfo,

		@Size(max = 50, message = "컬러 배리언트는 50자 이하여야 합니다.")
		String colorVariant,

		@DecimalMin(value = "0", message = "가격은 0 이상이어야 합니다.")
		@Digits(integer = 8, fraction = 0, message = "가격은 원 단위 정수여야 합니다.")
		BigDecimal price,

		String description,

		List<@NotBlank(message = "이미지 URL은 비어 있을 수 없습니다.")
		@Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.") String> imageUrls
) {
}
