plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.groove"
version = "0.0.1-SNAPSHOT"
description = "groove"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

jacoco {
    toolVersion = "0.8.13"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-mysql")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.14.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
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
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
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

// 인증/회원/카탈로그 도메인 라인 커버리지 80% 게이트.
// 인증·회원: #24 DoD. 카탈로그: #31 에서 W4 와 동일 정책으로 게이트 확장.
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
        // 인증/회원/카탈로그 하위 모든 패키지가 각각 80% 라인 커버리지를 충족해야 통과한다.
        rule {
            element = "PACKAGE"
            includes = listOf("com.groove.auth.*", "com.groove.member.*", "com.groove.catalog.*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
