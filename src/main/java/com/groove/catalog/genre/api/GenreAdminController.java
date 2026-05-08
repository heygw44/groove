package com.groove.catalog.genre.api;

import com.groove.catalog.genre.api.dto.GenreCreateRequest;
import com.groove.catalog.genre.api.dto.GenreResponse;
import com.groove.catalog.genre.api.dto.GenreUpdateRequest;
import com.groove.catalog.genre.application.GenreCommand;
import com.groove.catalog.genre.application.GenreService;
import com.groove.catalog.genre.domain.Genre;
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
@RestController
@RequestMapping("/api/v1/admin/genres")
@Validated
public class GenreAdminController {

    private final GenreService genreService;

    public GenreAdminController(GenreService genreService) {
        this.genreService = genreService;
    }

    @PostMapping
    public ResponseEntity<GenreResponse> create(@Valid @RequestBody GenreCreateRequest request) {
        Genre genre = genreService.create(new GenreCommand(request.name()));
        URI location = UriComponentsBuilder
                .fromPath("/api/v1/genres/{id}")
                .buildAndExpand(genre.getId())
                .toUri();
        return ResponseEntity.created(location).body(GenreResponse.from(genre));
    }

    @GetMapping
    public ResponseEntity<List<GenreResponse>> list() {
        List<GenreResponse> body = genreService.findAll().stream()
                .map(GenreResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GenreResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(GenreResponse.from(genreService.findById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GenreResponse> update(@PathVariable @Positive Long id,
                                                @Valid @RequestBody GenreUpdateRequest request) {
        Genre updated = genreService.update(id, new GenreCommand(request.name()));
        return ResponseEntity.ok(GenreResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        genreService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
