package com.groove.catalog.api;

import com.groove.catalog.api.dto.GenreResponse;
import com.groove.catalog.application.GenreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 장르 공개 조회 (API §3.4).
 *
 * <p>비로그인 사용자도 GET 으로 전체 목록을 조회할 수 있다.
 * 인증 경계는 {@code SecurityConfig} 의 {@code PUBLIC_GET_PATTERNS} 에 등록된다.
 */
@RestController
@RequestMapping("/api/v1/genres")
public class GenreQueryController {

    private final GenreService genreService;

    public GenreQueryController(GenreService genreService) {
        this.genreService = genreService;
    }

    @GetMapping
    public ResponseEntity<List<GenreResponse>> list() {
        List<GenreResponse> body = genreService.findAll().stream()
                .map(GenreResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }
}
