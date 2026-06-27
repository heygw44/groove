package com.groove.auth.api;

import com.groove.auth.api.dto.ChangePasswordRequest;
import com.groove.auth.application.AuthService;
import com.groove.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 변경(PATCH /members/me/password). 본인 식별은 AuthPrincipal 로만 한다(경로에 memberId 미노출). 성공 시 204.
 */
@Tag(name = "비밀번호", description = "로그인한 본인의 비밀번호 변경 (인증 필요 — 변경 시 모든 활성 세션 무효화)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/members/me/password")
public class MemberPasswordController {

    private final AuthService authService;

    public MemberPasswordController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "비밀번호 변경",
            description = "현재 비밀번호 확인 후 새 비밀번호로 변경한다. 새 비밀번호는 회원가입과 동일한 정책(최소 10자 + 영·숫·특수 각 1자 이상)을 "
                    + "따르며, 현재 비밀번호와 같으면 거부된다. 성공 시 본문 없이 204 이며 기존 활성 세션은 모두 무효화된다.")
    @ApiResponse(responseCode = "204", description = "비밀번호 변경 성공 (기존 활성 세션 전부 무효화)")
    @ApiResponse(responseCode = "400", description = "새 비밀번호 정책 위반 · 현재 비밀번호 불일치 · 새 비밀번호가 현재와 동일")
    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 미제공·만료·무효)")
    @ApiResponse(responseCode = "404", description = "활성 회원 없음 (탈퇴 후 토큰 만료 전 윈도)")
    @ApiResponse(responseCode = "429", description = "비밀번호 변경 Rate Limit 초과")
    @PatchMapping
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.memberId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
