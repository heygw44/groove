package com.groove.review.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;
import com.groove.review.dto.ReviewCreateRequest;
import com.groove.review.dto.ReviewEligibilityResponse;
import com.groove.review.dto.ReviewListRequest;
import com.groove.review.dto.ReviewResponse;
import com.groove.review.dto.ReviewSortType;
import com.groove.review.dto.ReviewUpdateRequest;
import com.groove.review.service.ReviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Review", description = "상품 리뷰")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;

	@Operation(summary = "상품 리뷰 목록 조회", description = "로그인 시 mine 포함")
	@SecurityRequirements
	@GetMapping("/products/{productId}/reviews")
	public ApiResponse<PageResponse<ReviewResponse>> getReviews(@PathVariable Long productId,
			@Valid @ModelAttribute ReviewListRequest request, @AuthMember(required = false) LoginMember loginMember) {
		// sort 는 서비스 위임 전에 검증해 정의되지 않은 값이면 즉시 COMMON_INVALID_INPUT 을 던진다.
		ReviewSortType.from(request.sort());
		return ApiResponse.ok(reviewService.getReviews(productId, memberIdOf(loginMember), request));
	}

	@Operation(summary = "리뷰 작성 가능 여부 조회")
	@SecurityRequirements
	@GetMapping("/products/{productId}/reviews/eligibility")
	public ApiResponse<ReviewEligibilityResponse> checkEligibility(@PathVariable Long productId,
			@AuthMember(required = false) LoginMember loginMember) {
		return ApiResponse.ok(reviewService.checkEligibility(productId, memberIdOf(loginMember)));
	}

	@Operation(summary = "리뷰 작성")
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping("/products/{productId}/reviews")
	public ApiResponse<ReviewResponse> create(@PathVariable Long productId,
			@Valid @RequestBody ReviewCreateRequest request, @AuthMember LoginMember loginMember) {
		return ApiResponse.ok(reviewService.create(productId, loginMember.id(), request));
	}

	@Operation(summary = "리뷰 수정")
	@PatchMapping("/reviews/{id}")
	public ApiResponse<ReviewResponse> update(@PathVariable Long id, @Valid @RequestBody ReviewUpdateRequest request,
			@AuthMember LoginMember loginMember) {
		return ApiResponse.ok(reviewService.update(id, loginMember.id(), request));
	}

	@Operation(summary = "리뷰 삭제")
	@DeleteMapping("/reviews/{id}")
	public ApiResponse<Void> delete(@PathVariable Long id, @AuthMember LoginMember loginMember) {
		reviewService.delete(id, loginMember.id());
		return ApiResponse.ok();
	}

	private static Long memberIdOf(LoginMember loginMember) {
		return loginMember == null ? null : loginMember.id();
	}
}
