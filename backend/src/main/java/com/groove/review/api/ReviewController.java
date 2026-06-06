package com.groove.review.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.review.api.dto.ReviewCreateRequest;
import com.groove.review.api.dto.ReviewResponse;
import com.groove.review.application.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "리뷰", description = "리뷰 작성 · 삭제 (모두 인증 회원 전용, 본인 주문/리뷰만 가능)")
@RestController
@RequestMapping("/api/v1/reviews")
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "리뷰 작성",
            description = "배송 완료(DELIVERED 이상)된 본인 주문에 포함된 앨범에 대해 리뷰를 작성한다. "
                    + "주문 1건-앨범 1개당 1개의 리뷰만 작성할 수 있으며, 게스트 주문에는 작성할 수 없다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "리뷰 작성 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (평점 범위·내용 길이 등)")
    @ApiResponse(responseCode = "401", description = "인증 필요 (토큰 없음·무효·만료)")
    @ApiResponse(responseCode = "403", description = "본인 주문이 아님 (게스트 주문 포함)")
    @ApiResponse(responseCode = "404", description = "주문 또는 앨범을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "이미 작성한 리뷰")
    @ApiResponse(responseCode = "422", description = "배송이 완료되지 않은 주문 · 주문에 포함되지 않은 앨범")
    @PostMapping
    public ResponseEntity<ReviewResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody ReviewCreateRequest request) {
        ReviewResponse response = reviewService.create(request.toCommand(principal.memberId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "리뷰 삭제",
            description = "본인이 작성한 리뷰를 삭제한다. 작성자가 아니면 삭제할 수 없다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "리뷰 삭제 성공 (본문 없음)")
    @ApiResponse(responseCode = "400", description = "reviewId 형식 오류 (양수가 아님)")
    @ApiResponse(responseCode = "401", description = "인증 필요 (토큰 없음·무효·만료)")
    @ApiResponse(responseCode = "403", description = "본인이 작성한 리뷰가 아님")
    @ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @Parameter(description = "삭제할 리뷰 식별자") @PathVariable @Positive Long reviewId) {
        reviewService.delete(principal.memberId(), reviewId);
    }
}
