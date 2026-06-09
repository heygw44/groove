package com.groove.common.seed;

import com.groove.member.application.MemberService;
import com.groove.member.application.SignupCommand;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ProductionSeedGuard} 통합 테스트.
 *
 * <p>가드는 {@code @Profile("!local & !test")} 라 {@code test} 프로파일에서는 빈으로 등록되지 않는다
 * (이 격리가 곧 가드의 핵심 — test 컨텍스트가 데모 계정을 커밋해도 가드에 막히지 않는다). 따라서
 * {@link MemberRepository} 를 주입받아 가드를 직접 생성하고 {@code run(null)} 으로 감지 로직만 검증한다.
 *
 * <p>데모 계정은 공유 컨테이너에 커밋되므로, 테스트가 만든 대표 계정만 {@code @BeforeEach}/{@code @AfterEach}
 * 에서 직접 정리한다(가드 테스트는 order/coupon 을 만들지 않아 FK 충돌 없이 member 단독 삭제가 안전).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class ProductionSeedGuardIntegrationTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberService memberService;
    @Autowired private PasswordEncoder passwordEncoder;

    private ProductionSeedGuard guard;

    @BeforeEach
    void setUp() {
        removeDemoAccounts();
        guard = new ProductionSeedGuard(memberRepository);
    }

    @AfterEach
    void tearDown() {
        removeDemoAccounts();
    }

    // 가드는 existsByEmailHash(soft-delete 포함)로 감지하므로, 정리도 같은 기준(email_hash)으로 지운다.
    // 평문 email 기준 필터는 익명화된 데모 계정(email 치환·email_hash 유지)을 놓쳐 공유 컨테이너 순서 의존
    // flake 를 유발하므로, 가드와 동일하게 emailHash 로 매칭한다.
    private void removeDemoAccounts() {
        Set<String> demoHashes = Set.of(
                Member.hashEmail(DemoAccounts.ADMIN_EMAIL),
                Member.hashEmail(DemoAccounts.DEMO_USER_EMAIL));
        memberRepository.findAll().stream()
                .filter(m -> demoHashes.contains(m.getEmailHash()))
                .forEach(memberRepository::delete);
    }

    @Test
    @DisplayName("데모 USER 계정이 존재하면 기동을 중단한다 (fail-fast)")
    void failsWhenDemoUserExists() {
        memberService.signup(new SignupCommand(
                DemoAccounts.DEMO_USER_EMAIL, "demo1234", "데모유저", "01000000000"));

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(DemoAccounts.DEMO_USER_EMAIL);
    }

    @Test
    @DisplayName("데모 ADMIN 계정만 있어도 기동을 중단한다")
    void failsWhenAdminExists() {
        Member admin = Member.registerAdmin(
                DemoAccounts.ADMIN_EMAIL, passwordEncoder.encode("admin1234"), "데모관리자", "01000000001");
        memberRepository.saveAndFlush(admin);

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(DemoAccounts.ADMIN_EMAIL);
    }

    @Test
    @DisplayName("데모 계정이 없으면 정상 통과한다")
    void passesWhenNoDemoAccounts() {
        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }
}
