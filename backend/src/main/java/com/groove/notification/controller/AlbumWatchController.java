package com.groove.notification.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;
import com.groove.notification.dto.AlbumWatchResponse;
import com.groove.notification.dto.AlbumWatchSearchRequest;
import com.groove.notification.service.AlbumWatchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "AlbumWatch", description = "앨범 새 프레싱 알림 구독")
@RestController
@RequestMapping("/api/v1/members/me/album-watches")
@RequiredArgsConstructor
public class AlbumWatchController {

	private final AlbumWatchService albumWatchService;

	@Operation(summary = "앨범 구독 등록")
	@PostMapping("/{albumId}")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<AlbumWatchResponse> add(@AuthMember LoginMember loginMember, @PathVariable Long albumId) {
		return ApiResponse.ok(albumWatchService.add(loginMember.id(), albumId));
	}

	@Operation(summary = "앨범 구독 해지")
	@DeleteMapping("/{albumId}")
	public ApiResponse<Void> remove(@AuthMember LoginMember loginMember, @PathVariable Long albumId) {
		albumWatchService.remove(loginMember.id(), albumId);
		return ApiResponse.ok();
	}

	@Operation(summary = "내 앨범 구독 목록 조회")
	@GetMapping
	public ApiResponse<PageResponse<AlbumWatchResponse>> getMyWatches(@AuthMember LoginMember loginMember,
			@Valid @ModelAttribute AlbumWatchSearchRequest request) {
		return ApiResponse.ok(albumWatchService.getMyWatches(loginMember.id(), request));
	}
}
