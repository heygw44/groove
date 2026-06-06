package com.groove.review.api;

import com.groove.common.api.PageResponse;
import com.groove.common.api.SortValidator;
import com.groove.review.api.dto.ReviewResponse;
import com.groove.review.application.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 상품별 리뷰 목록 API (API.md §3.8 — GET /albums/{id}/reviews).
 *
 * <p>공개 엔드포인트 — {@code SecurityConfig#PUBLIC_GET_PATTERNS} 의 {@code /api/v1/albums/**} 가 인증 경계를 담당한다.
 * 작성/삭제는 인증 전용이라 URL prefix({@code /reviews})가 달라 {@link ReviewController} 로 분리돼 있다.
 *
 * <p>정렬 화이트리스트: {@code createdAt} 만 허용 — Album 검색·회원 주문 목록과 같은 보안 패턴 (인덱스 없는 컬럼 정렬 차단).
 */
@Tag(name = "앨범 리뷰", description = "앨범별 리뷰 목록 조회 (비로그인 공개)")
@RestController
@RequestMapping("/api/v1/albums/{albumId}/reviews")
@Validated
public class AlbumReviewController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt");

    private final ReviewService reviewService;

    public AlbumReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "앨범 리뷰 목록",
            description = "특정 앨범에 작성된 리뷰를 페이지로 조회한다. 비로그인 공개 엔드포인트이며, 작성자 이름은 마스킹되어 노출된다. "
                    + "존재하지 않는 앨범이면 빈 페이지를 반환한다. 정렬은 createdAt 만 허용한다.")
    @ApiResponse(responseCode = "200", description = "앨범 리뷰 목록 조회 성공")
    @ApiResponse(responseCode = "400", description = "albumId 형식 오류 · 허용되지 않은 정렬 속성",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public ResponseEntity<PageResponse<ReviewResponse>> list(
            @Parameter(description = "리뷰를 조회할 앨범 식별자") @PathVariable @Positive Long albumId,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);

        Page<ReviewResponse> page = reviewService.listByAlbum(albumId, pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }
}
