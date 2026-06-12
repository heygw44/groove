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
 * 이메일 점유 해시 단일 진입점 (#186). 정규화(소문자화·trim) 후 서버 비밀키 기반 HMAC-SHA-256 으로
 * 인코딩한다.
 *
 * 왜 HMAC 인가: 기존 결정적 SHA-256(Member.hashEmail) 은 DB 유출 시 흔한 이메일 사전 대입으로 역추적될
 * 수 있었다 — 특히 탈퇴 회원은 평문이 익명화돼도 해시가 보존돼 원본 이메일 재식별 위험이 있었다(#170
 * 후속). 서버 비밀키를 키로 쓰는 HMAC 은 키 없이는 사전 대입이 불가능하다.
 *
 * 버전 prefix: 출력에 v1: 를 붙여 키 롤링에 대비한다 — 향후 키 교체 시 v2: 로 새로 계산하고 점유 검사는
 * 양 버전을 함께 본다. legacyHash 는 #186 이전의 prefix 없는 SHA-256(정규화) 값을 재현해, 마이그레이션
 * 이전 탈퇴 회원(평문 파기로 재계산 불가)의 점유를 가입 검사에서 함께 확인하는 데 쓴다. V18 백필
 * SHA2(LOWER(TRIM(email)),256) 과 동치다.
 *
 * 정규화: strip().toLowerCase() — Foo@x.com 과 foo@x.com 을 같은 이메일로 차단(패턴 A)하기 위함.
 * 저장(가입)과 검사(중복) 양쪽이 이 컴포넌트를 공유해 동일 시맨틱을 유지한다.
 *
 * 스레드 안전: Mac 은 stateful·비-thread-safe 라 싱글톤이 동시 호출되는 본 컴포넌트에서 공유하면 안
 * 된다. 따라서 Sha256Hasher 의 per-call MessageDigest.getInstance 와 동일하게 호출마다 Mac 인스턴스를
 * 만든다. 불변·thread-safe 한 SecretKeySpec 만 필드로 공유한다.
 */
@Component
public class EmailHasher {

    static final String VERSION_PREFIX = "v1:";
    private static final String ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private final SecretKeySpec signingKey;

    public EmailHasher(EmailHashProperties properties) {
        this.signingKey = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
        // HmacSHA256 가용성·키 적합성을 빈 생성 시점에 검증해 fail-fast (Sha256Hasher 정적 블록과 동일
        // 취지).
        newMac();
    }

    /**
     * 현재(v1) 점유 해시 — 저장·검사 양쪽이 공유하는 권위 값. "v1:" + HMAC-SHA256(정규화 이메일) 의
     * hex.
     */
    public String hash(String email) {
        return VERSION_PREFIX + hmacHex(normalize(email));
    }

    /**
     * #186 이전의 prefix 없는 SHA-256(정규화) 해시 — 마이그레이션 이전 탈퇴 회원(재계산 불가)의 점유를
     * 가입 검사에서 함께 확인하는 fallback 용. V18 의 SHA2(LOWER(TRIM(email)),256) 백필과 동치다.
     */
    public String legacyHash(String email) {
        return Sha256Hasher.hex(normalize(email));
    }

    /**
     * 가입 점유 검사용 해시 집합 — 현재(v1) hash + legacyHash(SHA-256). 마이그레이션 이전 탈퇴
     * 회원(재계산 불가)도 legacy 로 함께 차단한다. 점유 판정의 단일 출처 — MemberService.signup 과
     * ProductionSeedGuard 가 공유해, 향후 키 롤링(v2 추가) 시 이 한 곳만 고치면 모든 점유 검사 호출부가
     * 따라간다(#186). MemberRepository.existsByEmailHashIn 에 그대로 넘긴다.
     */
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
            // HmacSHA256 은 JCA 표준 알고리즘이고 키는 생성자에서 적합성이 검증되므로 도달 불가 —
            // 안전망.
            throw new IllegalStateException("HMAC-SHA256 초기화 실패", e);
        }
    }
}
