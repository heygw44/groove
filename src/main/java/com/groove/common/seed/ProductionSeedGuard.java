package com.groove.common.seed;

import com.groove.member.domain.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영(비-local) 프로파일 기동 시 데모 시드 유입을 차단하는 fail-fast 가드 (이슈 #128).
 *
 * <p>{@code application.yaml} 의 {@code spring.profiles.active} 기본값(:local 폴백)을 제거했으므로
 * 운영은 {@code default}({@code SPRING_PROFILES_ACTIVE} 미설정) 또는 {@code docker} 로 기동된다. 만에 하나
 * 과거 프로파일 오설정으로 {@link LocalDataSeeder} 가 운영 DB 에 데모 계정을 시드했다면, 알려진 비밀번호
 * ({@code admin1234} 등)로 외부 로그인·쿠폰 발급이 가능해 위험하다. 이 가드는 기동 시 데모 계정을 감지하면
 * {@link IllegalStateException} 을 던져 <b>기동을 중단</b>한다(운영자 즉시 인지 — {@code JwtProperties} 와 동일한 fail-fast).
 *
 * <h2>프로파일 조건</h2>
 * <p>{@code @Profile("!local & !test")} — {@code docker}·{@code default}(미설정 사각지대)·미래 {@code prod}
 * 를 모두 포함하고, {@code local}(시드가 정상 동작)과 {@code test}(통합 테스트가 데모 계정 32개를 커밋)는
 * 배제한다. {@code @Profile("docker")} 단독은 미설정(default) 경로를 놓쳐 이슈 #128 의 핵심 사각지대를 막지 못한다.
 */
@Component
@Profile("!local & !test")
public class ProductionSeedGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSeedGuard.class);

    private final MemberRepository memberRepository;

    public ProductionSeedGuard(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        // existsByEmail 은 soft-delete 포함 점유 확인이라 탈퇴 처리된 데모 계정도 잡아낸다.
        boolean hasAdmin = memberRepository.existsByEmail(DemoAccounts.ADMIN_EMAIL);
        boolean hasDemoUser = memberRepository.existsByEmail(DemoAccounts.DEMO_USER_EMAIL);
        if (hasAdmin || hasDemoUser) {
            String detected = hasAdmin ? DemoAccounts.ADMIN_EMAIL : DemoAccounts.DEMO_USER_EMAIL;
            throw new IllegalStateException(
                    "운영(비-local) 프로파일로 기동했으나 DB 에서 데모 계정(" + detected + ")이 감지되었습니다. "
                            + "LocalDataSeeder 의 데모 시드가 운영 DB 에 유입된 것으로 보입니다. 해당 계정/시드 데이터를 "
                            + "제거하거나, 로컬 환경이라면 SPRING_PROFILES_ACTIVE=local 로 기동하십시오. (이슈 #128)");
        }
        log.info("[seed-guard] 데모 계정 미감지 — 운영 시드 유입 가드 통과");
    }
}
