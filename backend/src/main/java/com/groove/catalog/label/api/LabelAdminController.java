package com.groove.catalog.label.api;

import com.groove.catalog.label.api.dto.LabelCreateRequest;
import com.groove.catalog.label.api.dto.LabelResponse;
import com.groove.catalog.label.api.dto.LabelUpdateRequest;
import com.groove.catalog.label.application.LabelCommand;
import com.groove.catalog.label.application.LabelService;
import com.groove.catalog.label.domain.Label;
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
 * 레이블 관리자 CRUD (API §3.9, ADMIN 전용). {@link GenreAdminController} 와 동일한 패턴.
 */
@Tag(name = "레이블 (관리자)", description = "레이블 등록·조회·수정·삭제 (ADMIN 권한 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/labels")
@Validated
public class LabelAdminController {

    private final LabelService labelService;

    public LabelAdminController(LabelService labelService) {
        this.labelService = labelService;
    }

    @Operation(summary = "레이블 등록",
            description = "레이블을 신규 등록한다. name 은 UNIQUE — 중복 시 409. 성공 시 Location 헤더에 생성된 리소스 URI 를 담는다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (이름 길이 등)")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @ApiResponse(responseCode = "409", description = "이미 등록된 레이블명 (중복)")
    @PostMapping
    public ResponseEntity<LabelResponse> create(@Valid @RequestBody LabelCreateRequest request) {
        Label label = labelService.create(new LabelCommand(request.name()));
        URI location = UriComponentsBuilder
                .fromPath("/api/v1/labels/{id}")
                .buildAndExpand(label.getId())
                .toUri();
        return ResponseEntity.created(location).body(LabelResponse.from(label));
    }

    @Operation(summary = "레이블 목록 조회",
            description = "전체 레이블을 id 오름차순 배열로 조회한다(페이징 없음). ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "조회 성공 — 레이블 배열")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @GetMapping
    public ResponseEntity<List<LabelResponse>> list() {
        List<LabelResponse> body = labelService.findAll().stream()
                .map(LabelResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "레이블 단건 조회",
            description = "ID 로 레이블을 조회한다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @ApiResponse(responseCode = "404", description = "레이블 미존재")
    @GetMapping("/{id}")
    public ResponseEntity<LabelResponse> get(@PathVariable @Positive @Parameter(description = "레이블 ID") Long id) {
        return ResponseEntity.ok(LabelResponse.from(labelService.findById(id)));
    }

    @Operation(summary = "레이블 수정",
            description = "레이블명을 수정한다. 다른 레이블과 이름이 중복되면 409. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (이름 길이 등)")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @ApiResponse(responseCode = "404", description = "레이블 미존재")
    @ApiResponse(responseCode = "409", description = "이미 등록된 레이블명 (중복)")
    @PutMapping("/{id}")
    public ResponseEntity<LabelResponse> update(@PathVariable @Positive @Parameter(description = "레이블 ID") Long id,
                                                @Valid @RequestBody LabelUpdateRequest request) {
        Label updated = labelService.update(id, new LabelCommand(request.name()));
        return ResponseEntity.ok(LabelResponse.from(updated));
    }

    @Operation(summary = "레이블 삭제",
            description = "레이블을 삭제한다. 해당 레이블을 참조하는 앨범이 있으면 삭제할 수 없다(409). ADMIN 권한 필요.")
    @ApiResponse(responseCode = "204", description = "삭제 성공 (본문 없음)")
    @ApiResponse(responseCode = "401", description = "미인증 (토큰 없음 · 만료 · 무효)")
    @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 아님)")
    @ApiResponse(responseCode = "404", description = "레이블 미존재")
    @ApiResponse(responseCode = "409", description = "해당 레이블을 사용하는 앨범이 있어 삭제 불가")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive @Parameter(description = "레이블 ID") Long id) {
        labelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
