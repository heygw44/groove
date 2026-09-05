package com.groove.product.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;
import com.groove.product.dto.AdminProductResponse;
import com.groove.product.dto.AdminProductSummaryResponse;
import com.groove.product.dto.ProductCreateRequest;
import com.groove.product.dto.ProductUpdateRequest;
import com.groove.product.entity.ProductStatus;
import com.groove.product.service.AdminProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Product", description = "관리자 상품 관리")
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

	private final AdminProductService adminProductService;

	@Operation(summary = "상품 등록")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<AdminProductResponse> create(@AuthMember LoginMember loginMember,
			@Valid @RequestBody ProductCreateRequest request) {
		return ApiResponse.ok(adminProductService.create(loginMember.id(), request));
	}

	@Operation(summary = "상품 수정")
	@PatchMapping("/{id}")
	public ApiResponse<AdminProductResponse> update(@AuthMember LoginMember loginMember, @PathVariable Long id,
			@Valid @RequestBody ProductUpdateRequest request) {
		return ApiResponse.ok(adminProductService.update(loginMember.id(), id, request));
	}

	@Operation(summary = "상품 숨김 처리")
	@DeleteMapping("/{id}")
	public ApiResponse<Void> hide(@AuthMember LoginMember loginMember, @PathVariable Long id) {
		adminProductService.hide(loginMember.id(), id);
		return ApiResponse.ok();
	}

	@Operation(summary = "상품 숨김 해제")
	@PatchMapping("/{id}/restore")
	public ApiResponse<AdminProductResponse> restore(@AuthMember LoginMember loginMember, @PathVariable Long id) {
		return ApiResponse.ok(adminProductService.restore(loginMember.id(), id));
	}

	@Operation(summary = "관리자 상품 단건 조회")
	@GetMapping("/{id}")
	public ApiResponse<AdminProductResponse> getDetail(@PathVariable Long id) {
		return ApiResponse.ok(adminProductService.getDetail(id));
	}

	@Operation(summary = "상품 목록 조회")
	@GetMapping
	public ApiResponse<PageResponse<AdminProductSummaryResponse>> getList(
			@RequestParam(required = false) ProductStatus status,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(adminProductService.getList(status, pageable));
	}
}
