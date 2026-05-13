package com.groove.review.api;

import com.groove.common.api.PageResponse;
import com.groove.common.api.SortValidator;
import com.groove.review.api.dto.ReviewResponse;
import com.groove.review.application.ReviewService;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
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
@RestController
@RequestMapping("/api/v1/albums/{albumId}/reviews")
@Validated
public class AlbumReviewController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt");

    private final ReviewService reviewService;

    public AlbumReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ReviewResponse>> list(
            @PathVariable @Positive Long albumId,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);

        Page<ReviewResponse> page = reviewService.listByAlbum(albumId, pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }
}
