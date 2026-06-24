import com.github.gradle.node.npm.task.NpmTask
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.node-gradle.node") version "7.1.0"
}

group = "com.groove"
version = "0.0.1-SNAPSHOT"
description = "groove"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// 소스 인코딩을 UTF-8 로 고정한다 — 한글 리터럴(예: TossMethodMapper 의 토스 결제수단 매칭)이
// 빌드 플랫폼 기본 charset 에 휘둘려 깨지지 않도록 한다. JDK 21 은 JEP 400 으로 기본 UTF-8 이나 명시한다.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

jacoco {
    toolVersion = "0.8.13"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // 카탈로그 조회 캐시 (#236). spring-context-support + CaffeineCacheManager 자동구성을 끌어온다 —
    // Caffeine(아래 ben-manes) 가 classpath 에 있으면 Caffeine provider 가 자동 선택된다.
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // OpenAPI/Swagger UI 자동 생성 (#156). 3.x 가 Spring Boot 4.x 호환 라인(2.8.x 는 Boot 3.5 용).
    // Spring Boot BOM 미관리 의존성이라 버전을 명시한다.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("org.flywaydb:flyway-mysql")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.19.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.mysql:mysql-connector-j")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// 로컬 개발 DX (#128): ./gradlew bootRun 을 그대로 쓰면 local 프로파일로 기동한다. application.yaml 의
// :local 폴백을 제거(운영 안전)했으므로 여기서 명시 주입한다. 단 셸에 SPRING_PROFILES_ACTIVE 가 있으면
// 그것을 존중하고(미주입), CLI override(--args='--spring.profiles.active=xxx')는 system property 보다
// 우선순위가 높아 그대로 통한다.
tasks.withType<BootRun> {
    if (System.getenv("SPRING_PROFILES_ACTIVE").isNullOrBlank()) {
        systemProperty("spring.profiles.active", "local")
    }
}

// 커버리지 측정 대상에서 제외 — 부트스트랩·DTO·@ConfigurationProperties 바인딩·패키지 메타만 제외한다.
// SecurityConfig·RateLimitFilterConfig 등은 운영 동작 직결 코드라 측정에 포함해 회귀를 잡는다.
val coverageExclusions = listOf(
    "com/groove/GrooveApplication.*",
    "com/groove/**/dto/**",
    "com/groove/**/package-info.*",
    "com/groove/**/*Properties.*",
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(coverageExclusions) }
        })
    )
}

// 인증/회원/카탈로그/쿠폰/주문/결제 도메인 라인 커버리지 80% 게이트 + 전체 라인 60% 게이트(#139).
// 인증·회원: #24 DoD. 카탈로그: #31 에서 W4 와 동일 정책으로 게이트 확장. 쿠폰: #93 (k6 부하·Before/After 와 함께 편입).
// 주문·결제: #139 (통합 테스트 보강과 함께 편입, 전체 60% BUNDLE 룰 동반).
// `check` 가 트리거하므로 ./gradlew check 가 임계값 위반 시 실패한다.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(coverageExclusions) }
        })
    )
    violationRules {
        // PACKAGE element 의 includes 는 패키지 FQN 과 매칭된다 (BUNDLE 의 includes 는 프로젝트명만 매칭되어 침묵 패스됨에 유의).
        // 인증/회원/카탈로그/쿠폰/주문/결제 하위 모든 패키지가 각각 80% 라인 커버리지를 충족해야 통과한다.
        // 주의: PACKAGE 룰은 매칭된 leaf 패키지마다 개별 평가된다 — 한 sub-package(예: com.groove.payment.gateway)만 미달해도 실패한다.
        rule {
            element = "PACKAGE"
            includes = listOf(
                "com.groove.auth.*", "com.groove.member.*", "com.groove.catalog.*", "com.groove.coupon.*",
                "com.groove.order.*", "com.groove.payment.*",
            )
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
        // 전체 라인 커버리지 60% 게이트 (#139). element 생략 = BUNDLE(리포트 전체).
        // includes 를 비워 둔다 — BUNDLE 에 FQN 을 includes 하면 프로젝트명만 매칭되어 침묵 패스되므로(위 주석).
        rule {
            limit {
                counter = "LINE"               // 생략 시 INSTRUCTION 기준이 되므로 LINE 명시 필수
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// ── 프론트엔드(Vue 3 + Vite) 빌드 통합 (#113) ────────────────────────────────────
// node-gradle 가 node 를 자동 다운로드(download=true)하므로 시스템 node 나 CI 셋업 없이
// ./gradlew build 한 번으로 프론트까지 빌드된다(메모의 'CI 프론트 미빌드' GOTCHA 해소).
// 백엔드만 빠르게 빌드하려면 ./gradlew build -PskipFrontend (값 없이도 동작).
// 값 없는 -P 는 빈 문자열이라 it.toBoolean()=false 가 되므로, isEmpty() 도 스킵으로 취급한다.
val skipFrontend = providers.gradleProperty("skipFrontend")
    .map { it.isEmpty() || it.toBoolean() }
    .getOrElse(false)

// node 버전 단일 소스: frontend/.node-version (npm run dev 는 nvm/fnm/volta 가 같은 파일을 읽어 버전 일치).
val nodeVersion = file("../frontend/.node-version").takeIf { it.exists() }?.readText()?.trim() ?: "22.12.0"

node {
    version.set(nodeVersion)
    download.set(true)
    nodeProjectDir.set(file("../frontend"))
}

// Vite 가 산출물을 src/main/resources/static 으로 출력한다(vite.config.js 의 outDir).
val frontendBuild = tasks.register<NpmTask>("frontendBuild") {
    group = "build"
    description = "Vite 로 Vue 프론트엔드를 빌드해 src/main/resources/static 으로 출력한다."
    dependsOn(tasks.named("npmInstall"))
    npmCommand.set(listOf("run", "build"))
    // 입력/출력 선언으로 소스 미변경 시 UP-TO-DATE → 백엔드만 자주 도는 개발 루프에서 npm 재빌드 회피.
    inputs.dir("../frontend/src")
    inputs.dir("../frontend/public")
    inputs.file("../frontend/index.html")
    inputs.file("../frontend/vite.config.js")
    inputs.file("../frontend/package.json")
    inputs.file("../frontend/package-lock.json")
    outputs.dir(layout.projectDirectory.dir("src/main/resources/static"))
    onlyIf { !skipFrontend }
}

// 백엔드만 빌드(-PskipFrontend)할 때도 통합 테스트가 SPA 셸을 찾도록 최소 placeholder index.html 을
// 보장한다(SecurityConfigIntegrationTest 의 GET / → 200 단언이 404 로 깨지는 것을 방지).
// 정상 빌드 시에는 frontendBuild 의 emptyOutDir 가 이 placeholder 를 실제 산출물로 덮어쓴다.
val frontendPlaceholder = tasks.register("frontendPlaceholder") {
    description = "skipFrontend 시 static/index.html placeholder 를 생성한다(통합 테스트 404 방지)."
    onlyIf { skipFrontend }
    doLast {
        val dir = layout.projectDirectory.dir("src/main/resources/static").asFile
        dir.mkdirs()
        val index = dir.resolve("index.html")
        if (!index.exists()) {
            index.writeText("<!doctype html>\n<title>groove (skipFrontend placeholder)</title>\n")
        }
    }
}

// bootJar/bootRun/build 가 모두 거치는 processResources 에 의존시켜 산출물이 jar 에 포함되게 한다.
tasks.named("processResources") {
    dependsOn(if (skipFrontend) frontendPlaceholder else frontendBuild)
}

// 빌드 산출 정적파일도 gradle clean 으로 정리(소스 진실은 frontend/, 산출물은 커밋 제외).
tasks.named<Delete>("clean") {
    delete("src/main/resources/static")
}
