package com.groove.catalog.api;

import com.groove.catalog.api.dto.LabelResponse;
import com.groove.catalog.application.LabelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 레이블 공개 조회. {@link GenreQueryController} 와 동일 패턴 (PUBLIC GET).
 */
@RestController
@RequestMapping("/api/v1/labels")
public class LabelQueryController {

    private final LabelService labelService;

    public LabelQueryController(LabelService labelService) {
        this.labelService = labelService;
    }

    @GetMapping
    public ResponseEntity<List<LabelResponse>> list() {
        List<LabelResponse> body = labelService.findAll().stream()
                .map(LabelResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }
}
