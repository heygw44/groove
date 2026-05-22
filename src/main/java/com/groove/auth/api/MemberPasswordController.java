package com.groove.auth.api;

import com.groove.auth.api.dto.ChangePasswordRequest;
import com.groove.auth.application.AuthService;
import com.groove.auth.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 변경 (#77, API.md §3.2 — PATCH /members/me/password).
 *
 * <p>URL 은 {@code /members/me} 하위지만 컨트롤러는 {@code auth} 패키지에 둔다 — 비밀번호는 인증
 * 크리덴셜이라 소유 도메인이 {@code auth} 이고, 변경 시 세션 무효화를 동기로 처리해야 하기 때문이다
 * ({@code member → auth} 패키지 결합 회피). URL ≠ 패키지.
 *
 * <p>{@code anyRequest().authenticated()} 기본 정책으로 보호되며 본인 식별은
 * {@code @AuthenticationPrincipal AuthPrincipal} 로만 한다 (경로에 memberId 미노출).
 * 성공 시 반환 본문이 없으므로 204 No Content.
 */
@RestController
@RequestMapping("/api/v1/members/me/password")
public class MemberPasswordController {

    private final AuthService authService;

    public MemberPasswordController(AuthService authService) {
        this.authService = authService;
    }

    @PatchMapping
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.memberId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
