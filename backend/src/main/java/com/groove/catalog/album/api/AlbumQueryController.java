package com.groove.catalog.album.api;

import com.groove.catalog.album.api.dto.AlbumDetailResponse;
import com.groove.catalog.album.api.dto.AlbumSearchRequest;
import com.groove.catalog.album.api.dto.AlbumSummaryResponse;
import com.groove.catalog.album.application.AlbumService;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.common.api.PageResponse;
import com.groove.common.api.SortValidator;
import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 앨범 공개 조회 (#34, API §3.3).
 *
 * <p>비로그인 사용자가 페이징·필터·키워드 검색을 통해 카탈로그를 조회한다.
 * 인증 경계는 {@code SecurityConfig#PUBLIC_GET_PATTERNS} 의 {@code /api/v1/albums/**} 가 담당.
 *
 * <p>의도적 N+1 (W10 시연 보존): 목록 조회 시 페치 조인 없이 lazy proxy 가 DTO 변환 단계에서
 * 풀리며 SELECT 가 N+1 발생한다 — {@link AlbumService#search} Javadoc 참조.
 */
@RestController
@RequestMapping("/api/v1/albums")
@Validated
public class AlbumQueryController {

    /**
     * 정렬 화이트리스트. {@code Pageable} 에 임의 필드를 노출하면 인덱스가 없는 컬럼으로 정렬되어
     * 운영 부하가 폭증할 수 있어 컨트롤러에서 명시 검증한다.
     *
     * <p>API §3.3 의 정렬 키 {@code salesCount} 는 W7 (주문 도메인) 집계라 본 이슈 범위에서
     * 제외하고, {@code id/createdAt/price/releaseYear} 만 허용한다.
     */
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "createdAt", "price", "releaseYear");

    private final AlbumService albumService;

    public AlbumQueryController(AlbumService albumService) {
        this.albumService = albumService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AlbumSummaryResponse>> search(
            @Valid @ModelAttribute AlbumSearchRequest request,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        rejectHiddenStatusFromPublic(request.status());
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);

        Page<AlbumSummaryResponse> page = albumService.search(request.toPublicCondition(), pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlbumDetailResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(albumService.findDetail(id));
    }

    /**
     * Public 검색 경로에서 HIDDEN 노출 차단. 관리자 전용 카테고리 (보유 재고/비공개) 가 검색 결과로
     * 새는 것을 막는다. 단건 GET 은 status 무관 — 직접 ID 를 알고 있는 경우 운영상 허용.
     */
    private void rejectHiddenStatusFromPublic(AlbumStatus status) {
        if (status == AlbumStatus.HIDDEN) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_FAILED,
                    "status=HIDDEN 은 공개 검색에서 사용할 수 없습니다");
        }
    }

}
