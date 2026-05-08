package com.groove.catalog.album.api;

import com.groove.catalog.album.api.dto.AlbumCreateRequest;
import com.groove.catalog.album.api.dto.AlbumResponse;
import com.groove.catalog.album.api.dto.AlbumUpdateRequest;
import com.groove.catalog.album.api.dto.StockAdjustRequest;
import com.groove.catalog.album.application.AlbumCommand;
import com.groove.catalog.album.application.AlbumService;
import com.groove.catalog.album.domain.Album;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 앨범 관리자 CRUD + 재고 조정 (API §3.9, ADMIN 전용).
 *
 * <p>인가 경계는 {@code SecurityConfig} 의 {@code /api/v1/admin/**} 패턴이 ROLE_ADMIN 으로 제약하므로
 * 컨트롤러는 추가 권한 어노테이션 없이 비즈니스 로직만 담당한다.
 *
 * <p>Public 검색/조회({@code GET /albums}, {@code GET /albums/{id}}) 는 W6 별도 이슈에서 도입한다.
 */
@RestController
@RequestMapping("/api/v1/admin/albums")
@Validated
public class AlbumAdminController {

    private final AlbumService albumService;

    public AlbumAdminController(AlbumService albumService) {
        this.albumService = albumService;
    }

    @PostMapping
    public ResponseEntity<AlbumResponse> create(@Valid @RequestBody AlbumCreateRequest request) {
        Album album = albumService.create(toCommand(request), request.stock());
        URI location = UriComponentsBuilder
                .fromPath("/api/v1/albums/{id}")
                .buildAndExpand(album.getId())
                .toUri();
        return ResponseEntity.created(location).body(AlbumResponse.from(album));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlbumResponse> update(@PathVariable @Positive Long id,
                                                @Valid @RequestBody AlbumUpdateRequest request) {
        Album updated = albumService.update(id, toCommand(request));
        return ResponseEntity.ok(AlbumResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        albumService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/stock")
    public ResponseEntity<AlbumResponse> adjustStock(@PathVariable @Positive Long id,
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
