package com.groove.common.seed;

import com.groove.member.domain.MemberRepository;
import com.groove.member.security.EmailHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 운영(비-local) 프로파일 기동 시 데모 계정을 감지하면 IllegalStateException 으로 기동을 중단하는 가드.
 * @Profile("!local & !test") 로 활성화된다.
 */
@Component
@Profile("!local & !test")
public class ProductionSeedGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSeedGuard.class);

    private final MemberRepository memberRepository;
    private final EmailHasher emailHasher;

    public ProductionSeedGuard(MemberRepository memberRepository, EmailHasher emailHasher) {
        this.memberRepository = memberRepository;
        this.emailHasher = emailHasher;
    }

    @Override
    public void run(ApplicationArguments args) {
        // existsByEmailHashIn 은 soft-delete(익명화) 포함 점유 확인이라 탈퇴 처리된 데모 계정도 잡아낸다.
        List<String> detected = new ArrayList<>();
        if (isOccupied(DemoAccounts.ADMIN_EMAIL)) detected.add(DemoAccounts.ADMIN_EMAIL);
        if (isOccupied(DemoAccounts.DEMO_USER_EMAIL)) detected.add(DemoAccounts.DEMO_USER_EMAIL);
        if (!detected.isEmpty()) {
            throw new IllegalStateException(
                    "운영(비-local) 프로파일로 기동했으나 DB 에서 데모 계정(" + String.join(", ", detected) + ")이 "
                            + "감지되었습니다. LocalDataSeeder 의 데모 시드가 운영 DB 에 유입된 것으로 보입니다. "
                            + "감지된 데모 계정/시드 데이터를 제거한 뒤 재기동하십시오. "
                            + "(로컬 개발 DB 라면 SPRING_PROFILES_ACTIVE=local 로 기동) (이슈 #128)");
        }
        log.info("[seed-guard] 데모 계정 미감지 — 운영 시드 유입 가드 통과");
    }

    /** v1(HMAC)·legacy(SHA-256) 해시를 함께 조회한다. */
    private boolean isOccupied(String email) {
        return memberRepository.existsByEmailHashIn(emailHasher.occupancyHashes(email));
    }
}
