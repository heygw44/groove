package com.groove.member.security;

import com.groove.support.MemberFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmailHasher 단위 테스트 (#186)")
class EmailHasherTest {

    // 시크릿을 단일 출처(application-test.yaml)에서 로드.
    private static final String SECRET = MemberFixtures.TEST_EMAIL_HASH_SECRET;
    // 키 의존성 검증용 — SECRET 과 다른 ≥32바이트 값.
    private static final String OTHER_SECRET = SECRET + "-variant";

    private final EmailHasher hasher = new EmailHasher(new EmailHashProperties(SECRET));

    @Test
    @DisplayName("hash → v1: prefix + 64자 소문자 hex")
    void hash_hasVersionPrefixAndHexBody() {
        String h = hasher.hash("user@example.com");

        assertThat(h).startsWith("v1:");
        assertThat(h.substring("v1:".length())).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("hash → 같은 입력·시크릿이면 결정적")
    void hash_isDeterministic() {
        assertThat(hasher.hash("user@example.com")).isEqualTo(hasher.hash("user@example.com"));
    }

    @Test
    @DisplayName("hash → 대소문자·앞뒤 공백 정규화로 동일 해시, 다른 이메일은 다른 해시 (패턴 A)")
    void hash_normalizesCaseAndWhitespace() {
        String base = hasher.hash("foo@example.com");

        assertThat(hasher.hash("FOO@example.com")).isEqualTo(base);
        assertThat(hasher.hash("  Foo@Example.com  ")).isEqualTo(base);
        assertThat(hasher.hash("other@example.com")).isNotEqualTo(base);
    }

    @Test
    @DisplayName("hash → 시크릿이 다르면 같은 이메일도 다른 해시 (키 기반 HMAC — 사전대입 방어의 핵심)")
    void hash_dependsOnSecret() {
        EmailHasher other = new EmailHasher(new EmailHashProperties(OTHER_SECRET));

        assertThat(other.hash("user@example.com")).isNotEqualTo(hasher.hash("user@example.com"));
    }

    @Test
    @DisplayName("hash 본문 = HMAC-SHA256(secret, 정규화 이메일) 독립 계산과 일치")
    void hash_matchesIndependentHmac() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal("user@example.com".getBytes(StandardCharsets.UTF_8)));

        // 대문자 입력도 정규화 후 같은 결과.
        assertThat(hasher.hash("USER@example.com")).isEqualTo("v1:" + expected);
    }

    @Test
    @DisplayName("legacyHash → prefix 없는 64자 hex, 정규화 동일, 시크릿 무관(SHA-256)")
    void legacyHash_isPrefixlessSha256() {
        String legacy = hasher.legacyHash("user@example.com");

        assertThat(legacy).hasSize(64).matches("[0-9a-f]{64}").doesNotStartWith("v1:");
        assertThat(hasher.legacyHash("USER@example.com")).isEqualTo(legacy);
        // legacy 는 SHA-256 이라 시크릿과 무관 — 다른 시크릿 EmailHasher 도 동일.
        assertThat(new EmailHasher(new EmailHashProperties(OTHER_SECRET)).legacyHash("user@example.com"))
                .isEqualTo(legacy);
    }

    @Test
    @DisplayName("hash ≠ legacyHash — v1·legacy 값 공간이 disjoint (UNIQUE 충돌 불가)")
    void hash_differsFromLegacy() {
        assertThat(hasher.hash("user@example.com")).isNotEqualTo(hasher.legacyHash("user@example.com"));
    }

    @Test
    @DisplayName("EmailHashProperties → 32바이트 미만 시크릿이면 기동 거부")
    void properties_rejectsShortSecret() {
        assertThatThrownBy(() -> new EmailHashProperties("short"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("EmailHashProperties → .env.example 플레이스홀더 시크릿이면 기동 거부 (#165)")
    void properties_rejectsPlaceholder() {
        assertThatThrownBy(() ->
                new EmailHashProperties("change-this-to-a-256-bit-email-hash-key-in-production"))
                .isInstanceOf(IllegalStateException.class);
    }
}
