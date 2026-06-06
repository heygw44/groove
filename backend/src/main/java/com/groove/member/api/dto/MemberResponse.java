package com.groove.member.api.dto;

import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 내 정보 응답 (API §3.2). {@code password} 는 절대 노출하지 않는다.
 */
public record MemberResponse(
        @Schema(description = "회원 ID", example = "1")
        Long memberId,

        @Schema(description = "이메일", example = "user@groove.com")
        String email,

        @Schema(description = "이름", example = "홍길동")
        String name,

        @Schema(description = "전화번호 (숫자만)", example = "01012345678")
        String phone,

        @Schema(description = "회원 권한", example = "USER")
        MemberRole role,

        @Schema(description = "이메일 인증 여부", example = "false")
        boolean emailVerified,

        @Schema(description = "가입 일시 (UTC)", example = "2026-01-01T00:00:00Z")
        Instant createdAt
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getPhone(),
                member.getRole(),
                member.isEmailVerified(),
                member.getCreatedAt()
        );
    }
}
