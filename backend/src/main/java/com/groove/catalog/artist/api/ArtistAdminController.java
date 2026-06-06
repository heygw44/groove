package com.groove.catalog.artist.api;

import com.groove.catalog.artist.api.dto.ArtistCreateRequest;
import com.groove.catalog.artist.api.dto.ArtistResponse;
import com.groove.catalog.artist.api.dto.ArtistUpdateRequest;
import com.groove.catalog.artist.application.ArtistCommand;
import com.groove.catalog.artist.application.ArtistService;
import com.groove.catalog.artist.domain.Artist;
import com.groove.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 아티스트 관리자 CRUD (API §3.9, ADMIN 전용).
 *
 * <p>인가 경계는 {@code SecurityConfig} 의 {@code /api/v1/admin/**} 패턴이 ROLE_ADMIN 으로 제약하므로
 * 컨트롤러는 추가 권한 어노테이션 없이 비즈니스 로직만 담당한다. 목록은 페이징 envelope 으로 응답한다.
 */
@Tag(name = "아티스트 (관리자)", description = "아티스트 등록·조회·수정·삭제 (ADMIN 권한 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/artists")
@Validated
public class ArtistAdminController {

    private final ArtistService artistService;

    public ArtistAdminController(ArtistService artistService) {
        this.artistService = artistService;
    }

    @Operation(summary = "아티스트 등록",
            description = "아티스트를 신규 등록한다. name 은 UNIQUE 제약이 없어 동명이인을 허용한다. 성공 시 Location 헤더에 생성된 리소스 URI 를 담는다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (이름 길이 등)")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @PostMapping
    public ResponseEntity<ArtistResponse> create(@Valid @RequestBody ArtistCreateRequest request) {
        Artist artist = artistService.create(new ArtistCommand(request.name(), request.description()));
        URI location = UriComponentsBuilder
                .fromPath("/api/v1/artists/{id}")
                .buildAndExpand(artist.getId())
                .toUri();
        return ResponseEntity.created(location).body(ArtistResponse.from(artist));
    }

    @Operation(summary = "아티스트 목록 조회",
            description = "아티스트를 페이징 envelope 으로 조회한다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "조회 성공 — 페이징 envelope")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @GetMapping
    public ResponseEntity<PageResponse<ArtistResponse>> list(
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<Artist> page = artistService.findAll(pageable);
        return ResponseEntity.ok(PageResponse.from(page, ArtistResponse::from));
    }

    @Operation(summary = "아티스트 단건 조회",
            description = "ID 로 아티스트를 조회한다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @ApiResponse(responseCode = "404", description = "아티스트 미존재")
    @GetMapping("/{id}")
    public ResponseEntity<ArtistResponse> get(@PathVariable @Positive @Parameter(description = "아티스트 ID") Long id) {
        return ResponseEntity.ok(ArtistResponse.from(artistService.findById(id)));
    }

    @Operation(summary = "아티스트 수정",
            description = "아티스트를 전체 갱신(PUT)한다. description 을 null 로 보내면 명시적 지움으로 처리된다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @ApiResponse(responseCode = "404", description = "아티스트 미존재")
    @PutMapping("/{id}")
    public ResponseEntity<ArtistResponse> update(@PathVariable @Positive @Parameter(description = "아티스트 ID") Long id,
                                                 @Valid @RequestBody ArtistUpdateRequest request) {
        Artist updated = artistService.update(id, new ArtistCommand(request.name(), request.description()));
        return ResponseEntity.ok(ArtistResponse.from(updated));
    }

    @Operation(summary = "아티스트 삭제",
            description = "아티스트를 삭제한다. 해당 아티스트를 참조하는 앨범이 있으면 삭제할 수 없다(409). ADMIN 권한 필요.")
    @ApiResponse(responseCode = "204", description = "삭제 성공 (본문 없음)")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @ApiResponse(responseCode = "404", description = "아티스트 미존재")
    @ApiResponse(responseCode = "409", description = "해당 아티스트를 사용하는 앨범이 있어 삭제 불가")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive @Parameter(description = "아티스트 ID") Long id) {
        artistService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
