package com.groove.catalog.artist.api;

import com.groove.catalog.album.api.dto.AlbumSearchRequest;
import com.groove.catalog.album.api.dto.AlbumSummaryResponse;
import com.groove.catalog.album.application.AlbumService;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.api.dto.ArtistResponse;
import com.groove.catalog.artist.application.ArtistService;
import com.groove.catalog.artist.domain.Artist;
import com.groove.common.api.PageResponse;
import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 아티스트 공개 조회 (API §3.3).
 *
 * <p>비로그인 사용자도 GET 으로 페이징 목록과 단건을 조회할 수 있다.
 * 인증 경계는 {@code SecurityConfig} 의 {@code PUBLIC_GET_PATTERNS} 에 등록된다.
 *
 * <p>{@code /artists/{id}/albums} 는 album 검색을 artistId 로 고정 위임한다 (#34) —
 * status=SELLING 강제, N+1 보존 정책은 {@code GET /albums} 와 동일.
 */
@Tag(name = "아티스트", description = "아티스트 공개 조회 (비로그인 — 목록·단건 및 아티스트별 앨범 검색)")
@RestController
@RequestMapping("/api/v1/artists")
@Validated
public class ArtistQueryController {

    /**
     * Album 정렬 화이트리스트. {@code AlbumQueryController} 와 동일 정책 (별도 이유: 동일한 album
     * 응답을 반환하므로 동일 키만 허용해야 함). 중복 정의는 의도적이다 — 한쪽에서만 추가/제거 시
     * 정책 드리프트가 일어나는 것을 즉각 알 수 있게 하기 위함.
     */
    private static final Set<String> ALLOWED_ALBUM_SORT_PROPERTIES = Set.of(
            "id", "createdAt", "price", "releaseYear");

    private final ArtistService artistService;
    private final AlbumService albumService;

    public ArtistQueryController(ArtistService artistService, AlbumService albumService) {
        this.artistService = artistService;
        this.albumService = albumService;
    }

    @Operation(summary = "아티스트 목록 조회",
            description = "아티스트를 페이징 조회한다. 기본 정렬은 id 오름차순.")
    @ApiResponse(responseCode = "200", description = "아티스트 목록 조회 성공")
    @GetMapping
    public ResponseEntity<PageResponse<ArtistResponse>> list(
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<Artist> page = artistService.findAll(pageable);
        return ResponseEntity.ok(PageResponse.from(page, ArtistResponse::from));
    }

    @Operation(summary = "아티스트 단건 조회",
            description = "ID 로 아티스트 단건을 조회한다.")
    @ApiResponse(responseCode = "200", description = "아티스트 조회 성공")
    @ApiResponse(responseCode = "400", description = "id 가 양수가 아님",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "해당 ID 의 아티스트 없음",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    public ResponseEntity<ArtistResponse> get(
            @Parameter(description = "조회할 아티스트 ID", example = "1") @PathVariable @Positive Long id) {
        return ResponseEntity.ok(ArtistResponse.from(artistService.findById(id)));
    }

    @Operation(summary = "아티스트별 앨범 검색",
            description = "지정한 아티스트의 앨범을 페이징 조회한다. 경로 artistId 가 강제되며 query 의 artistId 와 다르면 400. "
                    + "status=HIDDEN 차단·정렬 화이트리스트는 GET /albums 와 동일.")
    @ApiResponse(responseCode = "200", description = "아티스트별 앨범 목록 조회 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (status=HIDDEN, 경로·query artistId 불일치, 허용되지 않은 정렬 키 등)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "해당 ID 의 아티스트 없음",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}/albums")
    public ResponseEntity<PageResponse<AlbumSummaryResponse>> albums(
            @Parameter(description = "앨범을 조회할 아티스트 ID", example = "1") @PathVariable @Positive Long id,
            @Valid @ParameterObject @ModelAttribute AlbumSearchRequest request,
            @ParameterObject
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        artistService.findById(id);
        rejectHiddenStatusFromPublic(request.status());
        rejectArtistIdConflict(id, request.artistId());
        validateAlbumSort(pageable.getSort());

        Page<AlbumSummaryResponse> page = albumService.search(
                request.toPublicCondition().withArtistId(id), pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }

    private void rejectHiddenStatusFromPublic(AlbumStatus status) {
        if (status == AlbumStatus.HIDDEN) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_FAILED,
                    "status=HIDDEN 은 공개 검색에서 사용할 수 없습니다");
        }
    }

    /**
     * path 의 {@code id} 와 query 의 {@code artistId} 가 모두 지정되었는데 다르면 400.
     * 동일하거나 query 가 비어있으면 path 가 우선 적용된다 — silent override 방지.
     */
    private void rejectArtistIdConflict(Long pathArtistId, Long queryArtistId) {
        if (queryArtistId != null && !queryArtistId.equals(pathArtistId)) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_FAILED,
                    "경로의 artistId 와 query 의 artistId 가 일치하지 않습니다");
        }
    }

    private void validateAlbumSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_ALBUM_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new ValidationException(
                        ErrorCode.VALIDATION_FAILED,
                        "허용되지 않는 정렬 키: " + order.getProperty());
            }
        }
    }
}
