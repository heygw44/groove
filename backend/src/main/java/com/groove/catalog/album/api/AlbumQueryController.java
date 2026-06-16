package com.groove.catalog.album.api;

import com.groove.catalog.album.api.dto.AlbumDetailResponse;
import com.groove.catalog.album.api.dto.AlbumSearchRequest;
import com.groove.catalog.album.api.dto.AlbumSummaryResponse;
import com.groove.catalog.album.application.AlbumService;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.common.api.CursorCodec;
import com.groove.common.api.KeysetSort;
import com.groove.common.api.PageResponse;
import com.groove.common.api.ScrollResponse;
import com.groove.common.api.SortValidator;
import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 앨범 공개 조회 (#34, API §3.3).
 *
 * <p>비로그인 사용자가 페이징·필터·키워드 검색을 통해 카탈로그를 조회한다.
 * 인증 경계는 {@code SecurityConfig#PUBLIC_GET_PATTERNS} 의 {@code /api/v1/albums/**} 가 담당.
 *
 * <p>두 가지 페이징을 병행 제공한다: {@code GET /albums}(offset, {@link PageResponse})와
 * {@code GET /albums/scroll}(keyset 커서, {@link ScrollResponse}, #235). 깊은 페이지에서 {@code OFFSET N}
 * 스캔 비용을 피하려면 scroll 경로를 쓴다. 두 경로 모두 to-one 연관을 일괄 페치해(offset=@EntityGraph,
 * scroll=@BatchSize) DTO 변환 시 N+1 이 없다 — {@link AlbumService#search}/{@link AlbumService#searchKeyset}.
 */
@Tag(name = "앨범", description = "앨범 카탈로그 공개 조회 (비로그인 — 페이징·필터·키워드 검색 및 단건 상세)")
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

    /** 커서 페이징 윈도우 상한 — offset 의 {@code spring.data.web.pageable.max-page-size} 와 동일하게 맞춘다. */
    private static final int MAX_SCROLL_SIZE = 100;

    private final AlbumService albumService;
    private final CursorCodec cursorCodec;

    public AlbumQueryController(AlbumService albumService, CursorCodec cursorCodec) {
        this.albumService = albumService;
        this.cursorCodec = cursorCodec;
    }

    @Operation(summary = "앨범 목록 검색",
            description = "키워드·아티스트·장르·레이블·가격·발매연도·포맷 등으로 필터링한 앨범 목록을 페이징 조회한다. "
                    + "status 미지정 시 SELLING 으로 강제되며, 정렬은 id/createdAt/price/releaseYear 만 허용한다.")
    @ApiResponse(responseCode = "200", description = "앨범 목록 조회 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (status=HIDDEN 차단, 허용되지 않은 정렬 키, 잘못된 가격·연도 범위 등)")
    @GetMapping
    public ResponseEntity<PageResponse<AlbumSummaryResponse>> search(
            @Valid @ParameterObject @ModelAttribute AlbumSearchRequest request,
            @ParameterObject
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        rejectHiddenStatusFromPublic(request.status());
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);

        Page<AlbumSummaryResponse> page = albumService.search(request.toPublicCondition(), pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }

    @Operation(summary = "앨범 목록 커서(keyset) 검색",
            description = "GET /albums 와 동일한 필터·정렬을 keyset 커서 페이징으로 제공한다(#235). 깊은 페이지에서 "
                    + "offset 스캔 비용 없이 정렬 인덱스를 타고 전진한다. 첫 페이지는 cursor 없이 호출하고, 응답의 "
                    + "nextCursor 를 다음 요청 cursor 로 넘긴다. totalElements/totalPages 는 제공하지 않는다.")
    @ApiResponse(responseCode = "200", description = "앨범 목록 조회 성공 (다음 페이지 커서 포함)")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (status=HIDDEN 차단, 허용되지 않은 정렬 키, 잘못된 커서 등)")
    @GetMapping("/scroll")
    public ResponseEntity<ScrollResponse<AlbumSummaryResponse>> searchScroll(
            @Valid @ParameterObject @ModelAttribute AlbumSearchRequest request,
            @Parameter(description = "다음 페이지 커서 (첫 페이지는 생략)")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (1~100, 기본 20)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @ParameterObject
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Sort sort) {
        rejectHiddenStatusFromPublic(request.status());
        SortValidator.requireAllowed(sort, ALLOWED_SORT_PROPERTIES);

        Sort keysetSort = KeysetSort.withIdTiebreaker(sort);
        ScrollPosition position = cursorCodec.resolve(cursor, keysetSort);
        int limit = Math.clamp(size, 1, MAX_SCROLL_SIZE);

        Window<AlbumSummaryResponse> window =
                albumService.searchKeyset(request.toPublicCondition(), limit, keysetSort, position);
        return ResponseEntity.ok(ScrollResponse.from(window, cursorCodec, keysetSort));
    }

    @Operation(summary = "앨범 단건 상세 조회",
            description = "ID 로 앨범 상세를 조회한다. 단건 조회는 status 와 무관하게 허용된다 (ID 를 직접 아는 경우 운영상 노출).")
    @ApiResponse(responseCode = "200", description = "앨범 상세 조회 성공")
    @ApiResponse(responseCode = "400", description = "id 가 양수가 아님")
    @ApiResponse(responseCode = "404", description = "해당 ID 의 앨범 없음")
    @GetMapping("/{id}")
    public ResponseEntity<AlbumDetailResponse> get(
            @Parameter(description = "조회할 앨범 ID", example = "1") @PathVariable @Positive Long id) {
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
