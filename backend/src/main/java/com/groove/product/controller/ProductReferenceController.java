package com.groove.product.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.groove.global.common.ApiResponse;
import com.groove.product.dto.ArtistResponse;
import com.groove.product.dto.GenreResponse;
import com.groove.product.dto.LabelResponse;
import com.groove.product.service.ProductReferenceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Product Reference", description = "필터용 기준 데이터")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductReferenceController {

	private final ProductReferenceService productReferenceService;

	@Operation(summary = "장르 목록 조회")
	@SecurityRequirements
	@GetMapping("/genres")
	public ApiResponse<List<GenreResponse>> getGenres() {
		return ApiResponse.ok(productReferenceService.getGenres());
	}

	@Operation(summary = "레이블 목록 조회")
	@SecurityRequirements
	@GetMapping("/labels")
	public ApiResponse<List<LabelResponse>> getLabels() {
		return ApiResponse.ok(productReferenceService.getLabels());
	}

	@Operation(summary = "아티스트 검색")
	@SecurityRequirements
	@GetMapping("/artists")
	public ApiResponse<List<ArtistResponse>> searchArtists(@RequestParam(required = false) String keyword) {
		return ApiResponse.ok(productReferenceService.searchArtists(keyword));
	}

	@Operation(summary = "아티스트 단건 조회")
	@SecurityRequirements
	@GetMapping("/artists/{id}")
	public ApiResponse<ArtistResponse> getArtist(@PathVariable Long id) {
		return ApiResponse.ok(productReferenceService.getArtist(id));
	}
}
