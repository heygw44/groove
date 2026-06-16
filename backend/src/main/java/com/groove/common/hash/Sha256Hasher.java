package com.groove.common.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 임의 문자열의 SHA-256 해시를 64자 소문자 hex 로 인코딩하는 공용 유틸.
 * SHA-256 가용성은 클래스 로드 시점에 한 번 검증해 fail-fast 한다.
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
            // 도달 불가 — 안전망.
            throw new IllegalStateException(e);
        }
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HEX.formatHex(digest);
    }
}
