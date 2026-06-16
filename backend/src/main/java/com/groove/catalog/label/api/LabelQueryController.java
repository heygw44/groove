package com.groove.catalog.label.api;

import com.groove.catalog.label.api.dto.LabelResponse;
import com.groove.catalog.label.application.LabelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 레이블 공개 조회 (비로그인 GET 전체 목록).
 */
@Tag(name = "레이블", description = "레이블 공개 조회 (비로그인 — 전체 목록)")
@RestController
@RequestMapping("/api/v1/labels")
public class LabelQueryController {

    private final LabelService labelService;

    public LabelQueryController(LabelService labelService) {
        this.labelService = labelService;
    }

    @Operation(summary = "레이블 전체 목록 조회",
            description = "등록된 모든 레이블을 조회한다 (페이징 없음).")
    @ApiResponse(responseCode = "200", description = "레이블 목록 조회 성공")
    @GetMapping
    public ResponseEntity<List<LabelResponse>> list() {
        List<LabelResponse> body = labelService.findAll().stream()
                .map(LabelResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }
}
