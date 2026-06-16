package com.groove.support;

import com.groove.auth.security.AuthPrincipal;
import com.groove.member.domain.MemberRole;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 E2E 시나리오 검증용 보호 엔드포인트. test 프로파일에서만 활성화된다.
 */
@RestController
@RequestMapping("/api/v1/test")
@Profile("test")
public class TestSecuredController {

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return new MeResponse(principal.memberId(), principal.role());
    }

    public record MeResponse(Long memberId, MemberRole role) {
    }
}
