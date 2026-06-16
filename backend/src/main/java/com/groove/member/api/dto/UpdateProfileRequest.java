package com.groove.member.api.dto;

import com.groove.member.application.UpdateProfileCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 내 정보 수정 요청 (PATCH). 부분 수정: 전송하지 않은 필드(null)는 변경되지 않는다. 빈 문자열("")은 검증 대상이라
 * 400 으로 거부된다.
 */
public record UpdateProfileRequest(
        @Schema(description = "변경할 이름 (1~50자, 미전송 시 유지)", example = "홍길동", nullable = true)
        @Size(min = 1, max = 50)
        String name,

        @Schema(description = "변경할 전화번호 (숫자만 10~11자, 미전송 시 유지)", example = "01087654321", nullable = true)
        @Pattern(regexp = "^\\d{10,11}$", message = "전화번호는 숫자만 10~11자여야 합니다")
        String phone
) {
    public UpdateProfileCommand toCommand() {
        return new UpdateProfileCommand(name, phone);
    }
}
