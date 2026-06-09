package com.groove.common.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 임의 문자열의 SHA-256 해시를 64자 소문자 hex 로 인코딩하는 공용 유틸.
 *
 * <p>도메인 전반에서 재사용한다 — refresh 토큰 본문 해시({@code auth.TokenHasher}) 와
 * 회원 이메일 점유 해시({@code member.Member#hashEmail}) 가 동일 구현을 공유한다.
 * 특정 도메인 패키지에 두면 다른 도메인이 그 패키지에 의존하게 되므로(예: member → auth 순환)
 * 의존이 없는 {@code common} 에 둔다.
 *
 * <p>출력은 64자 소문자 hex 로 고정 — DB {@code CHAR(64)} 컬럼과 1:1 매핑된다.
 *
 * <p>SHA-256 가용성은 클래스 로드 시점에 한 번만 검증해 fail-fast 한다 — JCA 표준
 * 알고리즘이므로 런타임에 실패할 일은 없으나, 정적 초기화에서 명시적으로 검증해
 * 사용 시점의 예외 처리를 단순화한다.
 */
public final class Sha256Hasher {

    private static final String ALGORITHM = "SHA-256";
    private static final HexFormat HEX = HexFormat.of();

    static {
        try {
            MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘 사용 불가", e);
        }
    }

    private Sha256Hasher() {
    }

    public static String hex(String input) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // 정적 초기화 단계에서 검증되었으므로 도달 불가 — 안전망.
            throw new IllegalStateException(e);
        }
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HEX.formatHex(digest);
    }
}
