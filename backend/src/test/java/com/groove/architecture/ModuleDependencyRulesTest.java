package com.groove.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * 모듈 의존 규칙을 테스트로 고정한다 (#344, #349).
 *
 * 계층 순서(api → application → domain), 도메인 모델의 표현 계층 비의존, 그리고 도메인 간 단방향(순환 없음)을
 * 강제한다 (근거: docs/decisions/package-structure.md). 단일 모듈이라 컴파일러가 못 막는 경계를 보강한다.
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

    // application 은 api 의 컨트롤러·설정을 참조하면 안 된다. 경계 계약인 api.dto 는 예외이되,
    // 같은 도메인의 api.dto 로만 좁힌다(coupon→admin.api.dto 정리 완료, #349).
    @ArchTest
    static final ArchRule application_은_같은_도메인_api_dto_외의_api_에_의존하지_않는다 =
            classes()
                    .that().resideInAPackage("..application..")
                    .should(onlyDependOnApiViaSameDomainDto())
                    .because("application 은 api 의 컨트롤러·설정을 참조하면 안 되고, 경계 계약(api.dto)도 같은 도메인 것만 허용 (#349)");

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

    // ── (C) 도메인 간 단방향: 슬라이스 순환 없음 (#349) ───────────────────────────────

    @ArchTest
    static final ArchRule 도메인_슬라이스는_순환이_없다 =
            slices().matching("com.groove.(*)..").should().beFreeOfCycles()
                    .because("도메인 간 의존은 단방향으로 흐른다 — 가드 조회/시드의 역참조는 포트 인버전·시드 분리로 끊었다 (#349)");

    // ── helper ──────────────────────────────────────────────────────────────────

    /**
     * application 클래스가 api 패키지에 의존할 때, 같은 1단계 도메인의 api.dto 로만 허용하는 조건.
     * 다른 도메인의 api.dto·모든 컨트롤러/설정(api 비-dto)으로의 의존은 위반으로 보고한다.
     */
    private static ArchCondition<JavaClass> onlyDependOnApiViaSameDomainDto() {
        return new ArchCondition<>("api 의존은 같은 도메인의 api.dto 로만 한정") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                for (Dependency dependency : clazz.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    if (!isApiPackage(targetPackage)) {
                        continue;
                    }
                    boolean sameDomainDto = isApiDtoPackage(targetPackage)
                            && domainOf(clazz.getPackageName()).equals(domainOf(targetPackage));
                    if (!sameDomainDto) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static boolean isApiPackage(String packageName) {
        return packageName.matches("com\\.groove\\..*\\.api(\\..*)?");
    }

    private static boolean isApiDtoPackage(String packageName) {
        return packageName.matches("com\\.groove\\..*\\.api\\.dto(\\..*)?");
    }

    /** com.groove.&lt;domain&gt;... 의 1단계 도메인 세그먼트 추출 (예: com.groove.catalog.album.api.dto → catalog). */
    private static String domainOf(String packageName) {
        String prefix = "com.groove.";
        if (!packageName.startsWith(prefix)) {
            return "";
        }
        String rest = packageName.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
