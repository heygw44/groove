package com.groove.catalog.artist.api;

import com.groove.catalog.artist.api.dto.ArtistCreateRequest;
import com.groove.catalog.artist.api.dto.ArtistResponse;
import com.groove.catalog.artist.api.dto.ArtistUpdateRequest;
import com.groove.catalog.artist.application.ArtistCommand;
import com.groove.catalog.artist.application.ArtistService;
import com.groove.catalog.artist.domain.Artist;
import com.groove.common.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
@RestController
@RequestMapping("/api/v1/admin/artists")
public class ArtistAdminController {

    private final ArtistService artistService;

    public ArtistAdminController(ArtistService artistService) {
        this.artistService = artistService;
    }

    @PostMapping
    public ResponseEntity<ArtistResponse> create(@Valid @RequestBody ArtistCreateRequest request) {
        Artist artist = artistService.create(new ArtistCommand(request.name(), request.description()));
        URI location = UriComponentsBuilder
                .fromPath("/api/v1/artists/{id}")
                .buildAndExpand(artist.getId())
                .toUri();
        return ResponseEntity.created(location).body(ArtistResponse.from(artist));
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

    @PutMapping("/{id}")
    public ResponseEntity<ArtistResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody ArtistUpdateRequest request) {
        Artist updated = artistService.update(id, new ArtistCommand(request.name(), request.description()));
        return ResponseEntity.ok(ArtistResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        artistService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
