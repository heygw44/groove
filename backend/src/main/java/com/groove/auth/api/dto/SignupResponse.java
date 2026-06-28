package com.groove.auth.api.dto;

import com.groove.member.domain.Member;

import java.time.Instant;

public record SignupResponse(
        Long memberId,
        String email,
        String name,
        Instant createdAt
) {
    public static SignupResponse from(Member member) {
        return new SignupResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getCreatedAt()
        );
    }
}
