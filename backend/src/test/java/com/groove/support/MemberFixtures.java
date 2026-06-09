package com.groove.support;

import com.groove.member.domain.Member;
import com.groove.member.security.EmailHashProperties;
import com.groove.member.security.EmailHasher;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

/**
 * 테스트용 회원 픽스처 (#186).
 *
 * <p>{@code Member.register}/{@code registerAdmin} 는 #186 부터 미리 계산한 이메일 점유 해시를 인자로 요구한다
 * (해시가 서버 비밀키 HMAC 이라 엔티티에서 직접 계산 불가). 대다수 테스트는 해시 값 자체엔 관심이 없고 영속화된
 * Member 만 필요하므로, 기존 4-인자 호출과 동일한 시그니처로 해시를 자동 채워 주는 헬퍼를 모아 둔다.
 *
 * <p>해시는 Spring 빈이 아닌 직접 생성한 {@link EmailHasher} 로 계산한다 — 주 호출처 {@code MemberRepositoryTest}
 * 가 {@code @DataJpaTest} 슬라이스라 {@code @Component} 를 로딩하지 않기 때문이다. 시크릿은
 * {@code application-test.yaml} 의 {@code email.hash.secret} <b>단일 출처</b>에서 로드한다 —
 * {@code @SpringBootTest} 가 주입하는 EmailHasher 와 동일 시크릿을 보장하고, Java 에 시크릿 리터럴을
 * 두지 않기 위함이다(중복/drift 방지).
 */
public final class MemberFixtures {

    /** application-test.yaml 의 email.hash.secret 단일 출처에서 로드. */
    public static final String TEST_EMAIL_HASH_SECRET = loadTestEmailHashSecret();

    private static final EmailHasher HASHER = new EmailHasher(new EmailHashProperties(TEST_EMAIL_HASH_SECRET));

    private MemberFixtures() {
    }

    private static String loadTestEmailHashSecret() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-test.yaml"));
        Properties props = yaml.getObject();
        String secret = props == null ? null : props.getProperty("email.hash.secret");
        if (secret == null) {
            throw new IllegalStateException("application-test.yaml 에서 email.hash.secret 을 찾지 못했습니다");
        }
        return secret;
    }

    /** 현재(v1) 점유 해시. */
    public static String hash(String email) {
        return HASHER.hash(email);
    }

    /** #186 이전 prefix 없는 SHA-256 점유 해시 (legacy fallback 단언용). */
    public static String legacyHash(String email) {
        return HASHER.legacyHash(email);
    }

    /** 기존 {@code Member.register(email, passwordHash, name, phone)} 호환 — 점유 해시를 자동 채운다. */
    public static Member register(String email, String passwordHash, String name, String phone) {
        return Member.register(email, hash(email), passwordHash, name, phone);
    }

    /** 기존 {@code Member.registerAdmin(email, passwordHash, name, phone)} 호환 — 점유 해시를 자동 채운다. */
    public static Member registerAdmin(String email, String passwordHash, String name, String phone) {
        return Member.registerAdmin(email, hash(email), passwordHash, name, phone);
    }
}
