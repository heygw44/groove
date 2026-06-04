package com.groove.member.application;

/**
 * 내 정보 부분 수정 입력 (API DTO 와 분리). #76 / API.md §3.2.
 *
 * <p>부분 수정 규약: {@code null} 필드는 미변경. 도메인 {@code Member.updateProfile} 이 해석한다.
 */
public record UpdateProfileCommand(
        String name,
        String phone
) {
}
