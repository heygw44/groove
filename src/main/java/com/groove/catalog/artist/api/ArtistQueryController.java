package com.groove.catalog.artist.api;

import com.groove.catalog.artist.api.dto.ArtistResponse;
import com.groove.catalog.artist.application.ArtistService;
import com.groove.catalog.artist.domain.Artist;
import com.groove.common.api.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 아티스트 공개 조회 (API §3.3).
 *
 * <p>비로그인 사용자도 GET 으로 페이징 목록과 단건을 조회할 수 있다.
 * 인증 경계는 {@code SecurityConfig} 의 {@code PUBLIC_GET_PATTERNS} 에 등록된다.
 */
@RestController
@RequestMapping("/api/v1/artists")
public class ArtistQueryController {

    private final ArtistService artistService;

    public ArtistQueryController(ArtistService artistService) {
        this.artistService = artistService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ArtistResponse>> list(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<Artist> page = artistService.findAll(pageable);
        return ResponseEntity.ok(PageResponse.from(page, ArtistResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArtistResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(ArtistResponse.from(artistService.findById(id)));
    }
}
