package com.groove.review.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.review.api.dto.ReviewCreateRequest;
import com.groove.review.api.dto.ReviewResponse;
import com.groove.review.application.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리뷰 작성·삭제 API (API.md §3.8).
 *
 * <p>두 엔드포인트 모두 인증 회원 전용 — {@code SecurityConfig} 의 {@code anyRequest().authenticated()} 가 보호하며,
 * 본인 여부(작성 시 주문 소유, 삭제 시 리뷰 작성자)는 {@link ReviewService} 가 검증한다. 게스트 주문은 {@code memberId}
 * 가 없어 작성 단계에서 403 으로 걸린다.
 *
 * <p>상품별 리뷰 목록({@code GET /albums/{id}/reviews})은 공개 엔드포인트라 URL prefix 가 달라 {@link AlbumReviewController} 로 분리한다.
 */
@RestController
@RequestMapping("/api/v1/reviews")
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody ReviewCreateRequest request) {
        ReviewResponse response = reviewService.create(request.toCommand(principal.memberId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable @Positive Long reviewId) {
        reviewService.delete(principal.memberId(), reviewId);
    }
}
