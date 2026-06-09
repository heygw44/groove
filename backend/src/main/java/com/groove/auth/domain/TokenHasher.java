package com.groove.auth.domain;

import com.groove.common.hash.Sha256Hasher;

/**
 * Refresh 토큰 본문(JWT 문자열) 의 SHA-256 해시 hex 인코딩 유틸.
 *
 * <p>BCrypt 처럼 느린 해시는 매 갱신 요청에서 부담이 크고, refresh 토큰은
 * 충분히 큰 엔트로피(서명된 JWT) 를 가지므로 SHA-256 단방향 해시로 충분하다
 * (RFC 6749 / 6819 § 5.1.4.1.3 — 토큰은 충분히 길고 예측 불가능하므로 단순 해시 OK).
 *
 * <p>출력은 64자 소문자 hex 로 고정 — DB {@code CHAR(64)} 컬럼과 1:1 매핑된다.
 *
 * <p>실제 해시 계산은 공용 {@link Sha256Hasher} 에 위임한다 — refresh 토큰과 회원 이메일
 * 점유 해시가 동일 구현을 공유하되, 도메인별 의도(토큰 해시)는 이 타입의 이름으로 드러낸다.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String token) {
        return Sha256Hasher.hex(token);
    }
}
