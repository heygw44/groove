package com.groove.member.security;

import com.groove.common.hash.Sha256Hasher;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 이메일 점유 해시 단일 진입점. 정규화(소문자화·trim) 후 서버 비밀키 기반 HMAC-SHA-256 으로 인코딩한다.
 * 출력에 v1: prefix 를 붙여 키 롤링에 대비한다. Mac 은 비-thread-safe 라 호출마다 새로 만들고, SecretKeySpec
 * 만 필드로 공유한다.
 */
@Component
public class EmailHasher {

    static final String VERSION_PREFIX = "v1:";
    private static final String ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private final SecretKeySpec signingKey;

    public EmailHasher(EmailHashProperties properties) {
        this.signingKey = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
        // 빈 생성 시점에 HmacSHA256 가용성·키 적합성 검증(fail-fast).
        newMac();
    }

    /** 현재(v1) 점유 해시 — "v1:" + HMAC-SHA256(정규화 이메일) 의 hex. */
    public String hash(String email) {
        return VERSION_PREFIX + hmacHex(normalize(email));
    }

    /** prefix 없는 SHA-256(정규화) 해시 — 가입 검사 fallback 용. */
    public String legacyHash(String email) {
        return Sha256Hasher.hex(normalize(email));
    }

    /** 가입 점유 검사용 해시 집합 — 현재(v1) hash + legacyHash(SHA-256). */
    public List<String> occupancyHashes(String email) {
        return List.of(hash(email), legacyHash(email));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private String hmacHex(String input) {
        return HEX.formatHex(newMac().doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }

    private Mac newMac() {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(signingKey);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // 도달 불가 — 안전망.
            throw new IllegalStateException("HMAC-SHA256 초기화 실패", e);
        }
    }
}
