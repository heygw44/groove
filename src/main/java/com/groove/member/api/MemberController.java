package com.groove.member.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.member.api.dto.MemberResponse;
import com.groove.member.api.dto.UpdateProfileRequest;
import com.groove.member.application.MemberService;
import com.groove.member.domain.Member;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 본인 정보 조회·수정 (#76, API.md §3.2 — GET/PATCH /members/me).
 *
 * <p>{@link com.groove.order.api.MemberOrderController} 와 동일 패턴 — 항상 인증된 회원에게만
 * 노출되며 SecurityConfig 의 {@code anyRequest().authenticated()} 기본 정책으로 보호된다.
 * 본인 식별은 {@code @AuthenticationPrincipal AuthPrincipal} 로만 수행한다 (경로에 memberId 미노출).
 */
@RestController
@RequestMapping("/api/v1/members/me")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ResponseEntity<MemberResponse> getMyInfo(@AuthenticationPrincipal AuthPrincipal principal) {
        Member member = memberService.getMyInfo(principal.memberId());
        return ResponseEntity.ok(MemberResponse.from(member));
    }

    @PatchMapping
    public ResponseEntity<MemberResponse> updateMyInfo(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        Member member = memberService.updateMyInfo(principal.memberId(), request.toCommand());
        return ResponseEntity.ok(MemberResponse.from(member));
    }
}
