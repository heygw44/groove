package com.groove.product.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;
import com.groove.product.dto.AdminAlbumSummaryResponse;
import com.groove.product.service.AlbumService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Album", description = "관리자 앨범 조회")
@RestController
@RequestMapping("/api/v1/admin/albums")
@RequiredArgsConstructor
public class AdminAlbumController {

	private final AlbumService albumService;

	@Operation(summary = "앨범 목록 검색")
	@GetMapping
	public ApiResponse<PageResponse<AdminAlbumSummaryResponse>> getList(
			@RequestParam(required = false) String keyword,
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(albumService.getAdminList(keyword, pageable));
	}
}
