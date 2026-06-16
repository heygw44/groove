package com.groove.auth.domain;

import com.groove.common.hash.Sha256Hasher;

/**
 * Refresh 토큰 본문(JWT 문자열) 의 SHA-256 해시 hex 인코딩 유틸.
 *
 * <p>출력은 64자 소문자 hex 로 고정되어 DB CHAR(64) 컬럼과 1:1 매핑된다.
 *
 * <p>실제 해시 계산은 공용 Sha256Hasher 에 위임한다.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String token) {
        return Sha256Hasher.hex(token);
    }
}
