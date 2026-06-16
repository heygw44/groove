package com.groove.support;

import com.groove.member.domain.Member;
import com.groove.member.security.EmailHashProperties;
import com.groove.member.security.EmailHasher;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

/**
 * 테스트용 회원 픽스처. Member.register/registerAdmin 에 이메일 점유 해시를 자동 채워 주는 헬퍼를 모은다.
 * 해시는 직접 생성한 EmailHasher 로 계산하며, 시크릿은 application-test.yaml 의
 * email.hash.secret 단일 출처에서 로드한다.
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

    /** prefix 없는 SHA-256 점유 해시 (legacy fallback 단언용). */
    public static String legacyHash(String email) {
        return HASHER.legacyHash(email);
    }

    /** Member.register 에 점유 해시를 자동 채운다. */
    public static Member register(String email, String passwordHash, String name, String phone) {
        return Member.register(email, hash(email), passwordHash, name, phone);
    }

    /** Member.registerAdmin 에 점유 해시를 자동 채운다. */
    public static Member registerAdmin(String email, String passwordHash, String name, String phone) {
        return Member.registerAdmin(email, hash(email), passwordHash, name, phone);
    }
}
