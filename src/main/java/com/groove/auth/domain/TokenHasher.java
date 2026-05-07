package com.groove.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Refresh 토큰 본문(JWT 문자열) 의 SHA-256 해시 hex 인코딩 유틸.
 *
 * <p>BCrypt 처럼 느린 해시는 매 갱신 요청에서 부담이 크고, refresh 토큰은
 * 충분히 큰 엔트로피(서명된 JWT) 를 가지므로 SHA-256 단방향 해시로 충분하다
 * (RFC 6749 / 6819 § 5.1.4.1.3 — 토큰은 충분히 길고 예측 불가능하므로 단순 해시 OK).
 *
 * <p>출력은 64자 소문자 hex 로 고정 — DB {@code CHAR(64)} 컬럼과 1:1 매핑된다.
 *
 * <p>SHA-256 가용성은 클래스 로드 시점에 한 번만 검증해 fail-fast 한다 — JCA 표준
 * 알고리즘이므로 런타임에 실패할 일은 없으나, 정적 초기화에서 명시적으로 검증해
 * 사용 시점의 예외 처리를 단순화한다.
 */
public final class TokenHasher {

    private static final String ALGORITHM = "SHA-256";
    private static final HexFormat HEX = HexFormat.of();

    static {
        try {
            MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘 사용 불가", e);
        }
    }

    private TokenHasher() {
    }

    public static String sha256Hex(String token) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // 정적 초기화 단계에서 검증되었으므로 도달 불가 — 안전망.
            throw new IllegalStateException(e);
        }
        byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
        return HEX.formatHex(digest);
    }
}
