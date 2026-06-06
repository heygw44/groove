package com.groove.catalog.genre.api;

import com.groove.catalog.genre.api.dto.GenreCreateRequest;
import com.groove.catalog.genre.api.dto.GenreResponse;
import com.groove.catalog.genre.api.dto.GenreUpdateRequest;
import com.groove.catalog.genre.application.GenreCommand;
import com.groove.catalog.genre.application.GenreService;
import com.groove.catalog.genre.domain.Genre;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
import java.util.List;

/**
 * 장르 관리자 CRUD (API §3.9, ADMIN 전용).
 *
 * <p>인가 경계는 {@code SecurityConfig} 의 {@code /api/v1/admin/**} 패턴이 ROLE_ADMIN 으로 제약하므로
 * 컨트롤러는 추가 권한 어노테이션 없이 비즈니스 로직만 담당한다.
 */
@Tag(name = "장르 (관리자)", description = "장르 등록·조회·수정·삭제 (ADMIN 권한 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/genres")
@Validated
public class GenreAdminController {

    private final GenreService genreService;

    public GenreAdminController(GenreService genreService) {
        this.genreService = genreService;
    }

    @Operation(summary = "장르 등록",
            description = "장르를 신규 등록한다. name 은 UNIQUE — 중복 시 409. 성공 시 Location 헤더에 생성된 리소스 URI 를 담는다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (이름 길이 등)")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @ApiResponse(responseCode = "409", description = "이미 등록된 장르명 (중복)")
    @PostMapping
    public ResponseEntity<GenreResponse> create(@Valid @RequestBody GenreCreateRequest request) {
        Genre genre = genreService.create(new GenreCommand(request.name()));
        URI location = UriComponentsBuilder
                .fromPath("/api/v1/genres/{id}")
                .buildAndExpand(genre.getId())
                .toUri();
        return ResponseEntity.created(location).body(GenreResponse.from(genre));
    }

    @Operation(summary = "장르 목록 조회",
            description = "전체 장르를 id 오름차순 배열로 조회한다(페이징 없음). ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "조회 성공 — 장르 배열")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @GetMapping
    public ResponseEntity<List<GenreResponse>> list() {
        List<GenreResponse> body = genreService.findAll().stream()
                .map(GenreResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "장르 단건 조회",
            description = "ID 로 장르를 조회한다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @ApiResponse(responseCode = "404", description = "장르 미존재")
    @GetMapping("/{id}")
    public ResponseEntity<GenreResponse> get(@PathVariable @Positive @Parameter(description = "장르 ID") Long id) {
        return ResponseEntity.ok(GenreResponse.from(genreService.findById(id)));
    }

    @Operation(summary = "장르 수정",
            description = "장르명을 수정한다. 다른 장르와 이름이 중복되면 409. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (이름 길이 등)")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @ApiResponse(responseCode = "404", description = "장르 미존재")
    @ApiResponse(responseCode = "409", description = "이미 등록된 장르명 (중복)")
    @PutMapping("/{id}")
    public ResponseEntity<GenreResponse> update(@PathVariable @Positive @Parameter(description = "장르 ID") Long id,
                                                @Valid @RequestBody GenreUpdateRequest request) {
        Genre updated = genreService.update(id, new GenreCommand(request.name()));
        return ResponseEntity.ok(GenreResponse.from(updated));
    }

    @Operation(summary = "장르 삭제",
            description = "장르를 삭제한다. 해당 장르를 참조하는 앨범이 있으면 삭제할 수 없다(409). ADMIN 권한 필요.")
    @ApiResponse(responseCode = "204", description = "삭제 성공 (본문 없음)")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @ApiResponse(responseCode = "404", description = "장르 미존재")
    @ApiResponse(responseCode = "409", description = "해당 장르를 사용하는 앨범이 있어 삭제 불가")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive @Parameter(description = "장르 ID") Long id) {
        genreService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
