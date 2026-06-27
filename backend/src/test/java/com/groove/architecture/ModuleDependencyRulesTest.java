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
 * 계층 순서(api → application → domain)와 도메인 모델의 표현 계층 비의존을 강제한다
 * (근거: docs/decisions/package-structure.md). 단일 모듈이라 컴파일러가 못 막는 경계를 보강한다.
 *
 * 도메인 간 단방향(순환 없음)은 강제하지 않는다 — 가드 조회·시드에서 비롯한 실제 순환이 남아
 * beFreeOfCycles 가 통과 못 한다. 현황·해소는 ADR "한계" 절과 #349 참고.
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

    // api.dto 는 경계 계약이라 application 참조 허용(컨트롤러·설정만 금지). 예외는 도메인 무관 — ADR 이
    // 단방향 cross-domain 참조를 허용. same-domain 으로 좁히는 강화는 coupon→admin.api.dto 정리와 함께 #349.
    @ArchTest
    static final ArchRule application_은_api_컨트롤러에_의존하지_않는다 =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat(
                            resideInAPackage("..api..").and(not(resideInAPackage("..api.dto.."))))
                    .because("application 은 api 의 컨트롤러·설정을 참조하면 안 된다 (api.dto 는 경계 계약이라 예외)");

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
