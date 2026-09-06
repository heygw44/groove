package com.groove.catalog.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.catalog.dto.CatalogImportJobRequest;
import com.groove.catalog.dto.CatalogImportJobResponse;
import com.groove.catalog.dto.CatalogImportJobStartResponse;
import com.groove.catalog.dto.CatalogImportRequest;
import com.groove.catalog.dto.CatalogImportResponse;
import com.groove.catalog.dto.CatalogLookupRequest;
import com.groove.catalog.dto.CatalogLookupResponse;
import com.groove.catalog.dto.CatalogReleaseDetailResponse;
import com.groove.catalog.service.CatalogImportJobService;
import com.groove.catalog.service.CatalogImportService;
import com.groove.catalog.service.CatalogLookupService;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Catalog", description = "Discogs 카탈로그 조회 및 등록")
@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
public class AdminCatalogController {

	private final CatalogLookupService catalogLookupService;
	private final CatalogImportService catalogImportService;
	private final CatalogImportJobService catalogImportJobService;

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

	@Operation(summary = "Discogs 릴리즈 단건 즉시 등록")
	@PostMapping("/imports")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CatalogImportResponse> importRelease(@AuthMember LoginMember loginMember,
			@Valid @RequestBody CatalogImportRequest request) {
		return ApiResponse.ok(catalogImportService.importRelease(loginMember.id(), request));
	}

	@Operation(summary = "Discogs 마스터 릴리즈 전체 버전 배치 적재 시작")
	@PostMapping("/import-jobs")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CatalogImportJobStartResponse> startImportJob(@AuthMember LoginMember loginMember,
			@Valid @RequestBody CatalogImportJobRequest request) {
		return ApiResponse.ok(catalogImportJobService.start(loginMember.id(), request));
	}

	@Operation(summary = "카탈로그 적재 작업 목록 조회")
	@GetMapping("/import-jobs")
	public ApiResponse<PageResponse<CatalogImportJobResponse>> getImportJobs(
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ApiResponse.ok(catalogImportJobService.list(page, size));
	}

	@Operation(summary = "카탈로그 적재 작업 단건 조회")
	@GetMapping("/import-jobs/{jobExecutionId}")
	public ApiResponse<CatalogImportJobResponse> getImportJob(@PathVariable Long jobExecutionId) {
		return ApiResponse.ok(catalogImportJobService.get(jobExecutionId));
	}

	@Operation(summary = "실패한 카탈로그 적재 작업 재시작")
	@PostMapping("/import-jobs/{jobExecutionId}/restart")
	public ApiResponse<CatalogImportJobStartResponse> restartImportJob(@PathVariable Long jobExecutionId) {
		return ApiResponse.ok(catalogImportJobService.restart(jobExecutionId));
	}
}
