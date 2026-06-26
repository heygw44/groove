package com.groove.auth.domain;

import com.groove.common.hash.Sha256Hasher;

/**
 * Refresh 토큰 본문(JWT)의 SHA-256 해시 hex 인코딩 유틸. 출력은 64자 소문자 hex 로 DB CHAR(64) 와 1:1 매핑된다.
 * 실제 해시는 공용 Sha256Hasher 에 위임한다.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String token) {
        return Sha256Hasher.hex(token);
    }
}
