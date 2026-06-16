package com.groove.member.application;

/**
 * 내 정보 부분 수정 입력 (API DTO 와 분리). 부분 수정 규약: null 필드는 미변경.
 */
public record UpdateProfileCommand(
        String name,
        String phone
) {
}
