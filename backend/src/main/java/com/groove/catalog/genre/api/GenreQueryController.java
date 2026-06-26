package com.groove.catalog.genre.api;

import com.groove.catalog.genre.api.dto.GenreResponse;
import com.groove.catalog.genre.application.GenreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 장르 공개 조회 (비로그인 GET 전체 목록). */
@Tag(name = "장르", description = "장르 공개 조회 (비로그인 — 전체 목록)")
@RestController
@RequestMapping("/api/v1/genres")
public class GenreQueryController {

    private final GenreService genreService;

    public GenreQueryController(GenreService genreService) {
        this.genreService = genreService;
    }

    @Operation(summary = "장르 전체 목록 조회",
            description = "등록된 모든 장르를 조회한다 (페이징 없음).")
    @ApiResponse(responseCode = "200", description = "장르 목록 조회 성공")
    @GetMapping
    public ResponseEntity<List<GenreResponse>> list() {
        List<GenreResponse> body = genreService.findAll().stream()
                .map(GenreResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }
}
