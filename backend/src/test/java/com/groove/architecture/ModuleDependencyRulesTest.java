package com.groove.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 모듈 의존 규칙을 테스트로 고정한다 (#344).
 *
 * <p>{@code docs/decisions/package-structure.md} (ADR) 에 결정된 도메인 내부 계층 순서
 * (api → application → domain)와 도메인 모델의 표현 계층 비의존을 코드로 증명한다. 단일 Gradle
 * 모듈이라 컴파일러가 경계를 강제하지 못하므로, 그동안 코드리뷰로만 지키던 규율을 ArchUnit 으로
 * 보강해 회귀를 막는다.
 *
 * <p><b>도메인 간 단방향(순환 없음)은 의도적으로 강제하지 않는다.</b> ADR 은 "단방향"을 표방하지만,
 * 실제 코드에는 양방향 순환이 여럿 존재한다 — 앨범 삭제/회원 탈퇴/쿠폰 적용의 가드 조회 때문에
 * {@code order ↔ catalog}, {@code order ↔ coupon}, {@code order ↔ member} 가, 그 밖에
 * {@code catalog ↔ cart}, {@code coupon ↔ admin}, 그리고 시드({@code common.seed})가
 * {@code common → 도메인} 역참조를 만든다. 전역 {@code beFreeOfCycles} 를 켜려면 이벤트화·구조 변경이
 * 선행돼야 한다(테스트 전용 범위를 벗어남). 이 한계는 ADR 의 "한계/후속" 절에 기록한다.
 */
@AnalyzeClasses(packages = "com.groove", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleDependencyRulesTest {

    // ── (A) 계층 순서: api → application → domain (역방향 의존 금지) ──────────────────

    @ArchTest
    static final ArchRule domain_은_application_에_의존하지_않는다 =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..application..")
                    .because("도메인 계층은 가장 아래에 있어 오케스트레이션(application)을 거꾸로 참조하면 안 된다 (package-structure ADR)");

    @ArchTest
    static final ArchRule domain_은_api_에_의존하지_않는다 =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..api..")
                    .because("도메인 계층은 표현 계층(api)을 알아서는 안 된다 (package-structure ADR)");

    @ArchTest
    static final ArchRule application_은_api_컨트롤러에_의존하지_않는다 =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat(
                            resideInAPackage("..api..").and(not(resideInAPackage("..api.dto.."))))
                    .because("application 은 api 의 컨트롤러·설정을 참조하면 안 된다. "
                            + "단, 같은 도메인의 api.dto 는 요청/응답 경계 계약이라 허용한다 (예: OrderService 의 OrderCreateRequest/OrderResponse)");

    // ── (B) domain 패키지가 web/api 타입에 의존하지 않음 ─────────────────────────────

    @ArchTest
    static final ArchRule domain_은_web_이나_api_타입에_의존하지_않는다 =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.groove.web..", "..api..")
                    .because("도메인 모델은 웹/표현 계층 타입에서 자유로워야 한다 (package-structure ADR)");

    @ArchTest
    static final ArchRule domain_은_웹_프레임워크_타입에_의존하지_않는다 =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
                    .because("도메인 계층에 웹 프레임워크(서블릿·Spring Web) 타입이 새어들면 안 된다 (package-structure ADR)");
}
