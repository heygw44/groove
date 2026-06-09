package com.groove.common.seed;

import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 운영(비-local) 프로파일 기동 시 데모 시드 유입을 차단하는 fail-fast 가드 (이슈 #128).
 *
 * <p>{@code application.yaml} 의 {@code spring.profiles.active} 기본값(:local 폴백)을 제거했으므로
 * 운영은 {@code docker}(docker-compose 가 명시 주입) 로 기동된다. 만에 하나 과거 프로파일 오설정으로
 * {@link LocalDataSeeder} 가 운영 DB 에 데모 계정을 시드했다면, 알려진 비밀번호({@code admin1234} 등)로
 * 외부 로그인·쿠폰 발급이 가능해 위험하다. 이 가드는 기동 시 데모 계정을 감지하면 {@link IllegalStateException}
 * 을 던져 <b>기동을 중단</b>한다(운영자 즉시 인지 — {@code JwtProperties} 와 동일한 fail-fast).
 *
 * <h2>프로파일 조건</h2>
 * <p>{@code @Profile("!local & !test")} — 부팅 가능한 비-local 프로파일({@code docker}, 향후 실 PG 의 {@code prod})
 * 에서 활성화하고, {@code local}(시드 정상 동작)과 {@code test}(통합 테스트가 데모 계정을 커밋)는 배제한다.
 *
 * <p><b>참고</b>: {@code SPRING_PROFILES_ACTIVE} 미설정({@code default} 프로파일) 경로는 Mock PG 빈이 비-default
 * 프로파일 한정({@code @Profile({"local","dev","test","docker"})})이라 컨텍스트가 이 가드(ApplicationRunner)
 * 실행 전에 부팅 실패한다 — 데모 시드도 일어나지 않으므로({@code LocalDataSeeder} 가 {@code @Profile("local")})
 * 안전하며, 이 가드가 직접 보호하는 대상은 아니다.
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
        // existsByEmailHash 는 soft-delete(익명화) 포함 점유 확인이라 탈퇴 처리된 데모 계정도 잡아낸다 (#170
        // 부터 점유 판정이 평문 대신 정규화 이메일 해시 — 익명화로 평문이 치환돼도 해시는 보존되므로 그대로 감지).
        List<String> detected = new ArrayList<>();
        if (memberRepository.existsByEmailHash(Member.hashEmail(DemoAccounts.ADMIN_EMAIL))) detected.add(DemoAccounts.ADMIN_EMAIL);
        if (memberRepository.existsByEmailHash(Member.hashEmail(DemoAccounts.DEMO_USER_EMAIL))) detected.add(DemoAccounts.DEMO_USER_EMAIL);
        if (!detected.isEmpty()) {
            throw new IllegalStateException(
                    "운영(비-local) 프로파일로 기동했으나 DB 에서 데모 계정(" + String.join(", ", detected) + ")이 "
                            + "감지되었습니다. LocalDataSeeder 의 데모 시드가 운영 DB 에 유입된 것으로 보입니다. "
                            + "감지된 데모 계정/시드 데이터를 제거한 뒤 재기동하십시오. "
                            + "(로컬 개발 DB 라면 SPRING_PROFILES_ACTIVE=local 로 기동) (이슈 #128)");
        }
        log.info("[seed-guard] 데모 계정 미감지 — 운영 시드 유입 가드 통과");
    }
}
