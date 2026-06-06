package com.groove.catalog.album.api;

import com.groove.catalog.album.api.dto.AlbumCreateRequest;
import com.groove.catalog.album.api.dto.AlbumResponse;
import com.groove.catalog.album.api.dto.AlbumSearchRequest;
import com.groove.catalog.album.api.dto.AlbumSummaryResponse;
import com.groove.catalog.album.api.dto.AlbumUpdateRequest;
import com.groove.catalog.album.api.dto.StockAdjustRequest;
import com.groove.catalog.album.application.AlbumCommand;
import com.groove.catalog.album.application.AlbumService;
import com.groove.catalog.album.domain.Album;
import com.groove.common.api.PageResponse;
import com.groove.common.api.SortValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Set;

/**
 * 앨범 관리자 CRUD + 재고 조정 (API §3.9, ADMIN 전용).
 *
 * <p>인가 경계는 {@code SecurityConfig} 의 {@code /api/v1/admin/**} 패턴이 ROLE_ADMIN 으로 제약하므로
 * 컨트롤러는 추가 권한 어노테이션 없이 비즈니스 로직만 담당한다.
 *
 * <p>Public 검색/조회({@code GET /albums}, {@code GET /albums/{id}}) 는 {@code AlbumQueryController} 가
 * 담당한다. 본 컨트롤러의 {@code GET} 은 관리자 콘솔(#119) 전용 목록으로, Public 과 달리 HIDDEN 을 포함한
 * 전체 status 를 노출한다({@link AlbumSearchRequest#toAdminCondition()}).
 */
@Tag(name = "앨범 (관리자)", description = "앨범 등록·수정·삭제·재고 조정 및 관리자 목록 조회 (ADMIN 권한 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/albums")
@Validated
public class AlbumAdminController {

    /**
     * 정렬 화이트리스트. {@code AlbumQueryController} 와 동일 정책 — 인덱스 없는 컬럼 정렬 차단.
     */
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "createdAt", "price", "releaseYear");

    private final AlbumService albumService;

    public AlbumAdminController(AlbumService albumService) {
        this.albumService = albumService;
    }

    /**
     * 관리자 앨범 목록(#119). Public 검색과 동일한 필터·정렬을 쓰되 status 를 강제하지 않아
     * HIDDEN 을 포함한 전체 status 가 조회된다 — 비공개 앨범도 관리/수정/삭제할 수 있어야 하기 때문.
     */
    @Operation(summary = "앨범 관리자 목록 조회",
            description = "관리자 콘솔 전용 앨범 목록. Public 검색과 동일한 필터·정렬을 쓰되 status 를 강제하지 않아 HIDDEN 을 포함한 전체 status 가 조회된다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "조회 성공 — 페이징 envelope")
    @ApiResponse(responseCode = "400", description = "허용되지 않은 정렬 컬럼 또는 검색 파라미터 검증 실패",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public ResponseEntity<PageResponse<AlbumSummaryResponse>> list(
            @Valid @ParameterObject @ModelAttribute AlbumSearchRequest request,
            @ParameterObject
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);
        Page<AlbumSummaryResponse> page = albumService.search(request.toAdminCondition(), pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }

    @Operation(summary = "앨범 등록",
            description = "앨범을 신규 등록한다. artist/genre 는 필수 FK, label 은 nullable. 성공 시 Location 헤더에 생성된 앨범 리소스 URI 를 담는다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (제목·가격·연도·재고 등)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "참조한 아티스트 · 장르 · 레이블 미존재",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<AlbumResponse> create(@Valid @RequestBody AlbumCreateRequest request) {
        Album album = albumService.create(toCommand(request), request.stock());
        URI location = UriComponentsBuilder
                .fromPath("/api/v1/albums/{id}")
                .buildAndExpand(album.getId())
                .toUri();
        return ResponseEntity.created(location).body(AlbumResponse.from(album));
    }

    @Operation(summary = "앨범 수정",
            description = "앨범을 전체 갱신(PUT)한다. 재고(stock)는 본 요청 대상이 아니며 PATCH /stock 으로만 변경한다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "앨범 또는 참조한 아티스트 · 장르 · 레이블 미존재",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PutMapping("/{id}")
    public ResponseEntity<AlbumResponse> update(@PathVariable @Positive @Parameter(description = "앨범 ID") Long id,
                                                @Valid @RequestBody AlbumUpdateRequest request) {
        Album updated = albumService.update(id, toCommand(request));
        return ResponseEntity.ok(AlbumResponse.from(updated));
    }

    @Operation(summary = "앨범 삭제",
            description = "앨범을 삭제한다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "204", description = "삭제 성공 (본문 없음)")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "앨범 미존재",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive @Parameter(description = "앨범 ID") Long id) {
        albumService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "앨범 재고 조정",
            description = "재고를 delta 만큼 증감한다. delta 는 음수도 허용(반품·재고 감소)하나 결과 재고가 0 미만이면 거부된다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "재고 조정 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 또는 결과 재고가 0 미만",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "앨범 미존재",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PatchMapping("/{id}/stock")
    public ResponseEntity<AlbumResponse> adjustStock(@PathVariable @Positive @Parameter(description = "앨범 ID") Long id,
                                                    @Valid @RequestBody StockAdjustRequest request) {
        Album adjusted = albumService.adjustStock(id, request.delta());
        return ResponseEntity.ok(AlbumResponse.from(adjusted));
    }

    private static AlbumCommand toCommand(AlbumCreateRequest req) {
        return new AlbumCommand(
                req.title(),
                req.artistId(),
                req.genreId(),
                req.labelId(),
                req.releaseYear(),
                req.format(),
                req.price(),
                req.status(),
                req.isLimited(),
                req.coverImageUrl(),
                req.description()
        );
    }

    private static AlbumCommand toCommand(AlbumUpdateRequest req) {
        return new AlbumCommand(
                req.title(),
                req.artistId(),
                req.genreId(),
                req.labelId(),
                req.releaseYear(),
                req.format(),
                req.price(),
                req.status(),
                req.isLimited(),
                req.coverImageUrl(),
                req.description()
        );
    }
}
