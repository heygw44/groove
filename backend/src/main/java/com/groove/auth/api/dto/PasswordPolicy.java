package com.groove.auth.api.dto;

/**
 * 비밀번호 검증 정책 상수. {@code SignupRequest} 와 {@code ChangePasswordRequest} 가 동일한 규칙을
 * 공유하도록 한곳에 모은다 — 보안 핵심 규칙이 두 DTO 에 중복되어 한쪽만 바뀌는 drift 를 방지한다.
 *
 * <p>모두 컴파일 타임 상수(static final)라 Bean Validation 애너테이션 속성으로 사용할 수 있다.
 */
public final class PasswordPolicy {

    /** 최소 10자. */
    public static final int MIN_LENGTH = 10;

    /** BCrypt 72-byte 한계. */
    public static final int MAX_LENGTH = 72;

    /** 영문·숫자·특수문자 각 1자 이상. */
    public static final String COMPLEXITY_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$";

    public static final String LENGTH_MESSAGE = "비밀번호는 10~72자여야 합니다 (BCrypt 72-byte 한계)";

    public static final String COMPLEXITY_MESSAGE = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다";

    private PasswordPolicy() {
    }
}
