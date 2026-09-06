package com.groove.catalog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.catalog.dto.CatalogLookupRequest;
import com.groove.catalog.dto.CatalogLookupResponse;
import com.groove.catalog.dto.CatalogReleaseDetailResponse;
import com.groove.catalog.service.CatalogLookupService;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Catalog", description = "Discogs 카탈로그 조회")
@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
public class AdminCatalogController {

	private final CatalogLookupService catalogLookupService;

	@Operation(summary = "Discogs 릴리즈 검색", description = "바코드/카탈로그번호/자유 키워드 중 최소 하나 필요")
	@GetMapping("/lookup")
	public ApiResponse<PageResponse<CatalogLookupResponse>> lookup(
			@Valid @ModelAttribute CatalogLookupRequest request) {
		return ApiResponse.ok(catalogLookupService.lookup(request));
	}

	@Operation(summary = "Discogs 릴리즈 상세 조회", description = "등록 폼 프리필용")
	@GetMapping("/releases/{discogsReleaseId}")
	public ApiResponse<CatalogReleaseDetailResponse> getRelease(@PathVariable long discogsReleaseId) {
		return ApiResponse.ok(catalogLookupService.getRelease(discogsReleaseId));
	}
}
