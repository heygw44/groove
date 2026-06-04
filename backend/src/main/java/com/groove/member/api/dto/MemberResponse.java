package com.groove.member.api.dto;

import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRole;

import java.time.Instant;

/**
 * 내 정보 응답 (API §3.2). {@code password} 는 절대 노출하지 않는다.
 */
public record MemberResponse(
        Long memberId,
        String email,
        String name,
        String phone,
        MemberRole role,
        boolean emailVerified,
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
