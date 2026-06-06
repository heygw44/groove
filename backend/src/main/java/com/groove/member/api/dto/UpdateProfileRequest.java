package com.groove.member.api.dto;

import com.groove.member.application.UpdateProfileCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 내 정보 수정 요청 (API §3.2, PATCH).
 *
 * <p>부분 수정: 전송하지 않은 필드(null)는 변경되지 않는다. 검증 규칙은 {@code SignupRequest} 의
 * name·phone 과 동일하되 {@code @NotBlank} 는 제외한다 — Jakarta Validation 스펙상 {@code @Size}·
 * {@code @Pattern} 은 null 을 유효로 간주하므로(미전송=검증 스킵=미변경) 부분 수정 규약이 그대로 성립한다.
 *
 * <p>주의: 빈 문자열("")은 null 이 아니므로 검증 대상이다 — name 은 {@code @Size(min=1)} 로,
 * phone 은 {@code @Pattern} 으로 거부되어 400 이 된다.
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
