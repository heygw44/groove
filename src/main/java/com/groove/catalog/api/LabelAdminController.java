package com.groove.catalog.api;

import com.groove.catalog.api.dto.LabelCreateRequest;
import com.groove.catalog.api.dto.LabelResponse;
import com.groove.catalog.api.dto.LabelUpdateRequest;
import com.groove.catalog.application.LabelCommand;
import com.groove.catalog.application.LabelService;
import com.groove.catalog.domain.Label;
import jakarta.validation.Valid;
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
import java.util.List;

/**
 * 레이블 관리자 CRUD (API §3.9, ADMIN 전용). {@link GenreAdminController} 와 동일한 패턴.
 */
@RestController
@RequestMapping("/api/v1/admin/labels")
public class LabelAdminController {

    private final LabelService labelService;

    public LabelAdminController(LabelService labelService) {
        this.labelService = labelService;
    }

    @PostMapping
    public ResponseEntity<LabelResponse> create(@Valid @RequestBody LabelCreateRequest request) {
        Label label = labelService.create(new LabelCommand(request.name()));
        URI location = UriComponentsBuilder
                .fromPath("/api/v1/labels/{id}")
                .buildAndExpand(label.getId())
                .toUri();
        return ResponseEntity.created(location).body(LabelResponse.from(label));
    }

    @GetMapping
    public ResponseEntity<List<LabelResponse>> list() {
        List<LabelResponse> body = labelService.findAll().stream()
                .map(LabelResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LabelResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(LabelResponse.from(labelService.findById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LabelResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody LabelUpdateRequest request) {
        Label updated = labelService.update(id, new LabelCommand(request.name()));
        return ResponseEntity.ok(LabelResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        labelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
