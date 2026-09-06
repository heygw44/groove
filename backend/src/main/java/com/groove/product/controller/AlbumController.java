package com.groove.product.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.global.common.ApiResponse;
import com.groove.product.dto.AlbumDetailResponse;
import com.groove.product.service.AlbumService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Album", description = "앨범 조회")
@RestController
@RequestMapping("/api/v1/albums")
@RequiredArgsConstructor
public class AlbumController {

	private final AlbumService albumService;

	@Operation(summary = "앨범 상세 + 프레싱 목록 조회")
	@SecurityRequirements
	@GetMapping("/{id}")
	public ApiResponse<AlbumDetailResponse> getDetail(@PathVariable Long id) {
		return ApiResponse.ok(albumService.getDetail(id));
	}
}
