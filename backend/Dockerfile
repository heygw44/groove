# 빌드 컨텍스트 = 저장소 루트(docker-compose 의 context: .), dockerfile = backend/Dockerfile (#131).
# 컨테이너 안에서도 backend/ ↔ ../frontend 형제 관계를 그대로 재현해 통합(UI 포함) JAR 을 만든다.
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# 의존성 레이어 캐싱: 빌드 스크립트·래퍼만 먼저 COPY 해 dependencies 를 받아둔다.
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/
COPY backend/gradle/ backend/gradle/
WORKDIR /workspace/backend
RUN ./gradlew dependencies --no-daemon || true

# 소스 + 프론트엔드 COPY 후 bootJar. processResources → frontendBuild(= ../frontend 에서 npm run build)가
# Vite 산출물을 backend/src/main/resources/static 으로 출력해 단일 JAR 에 UI 가 포함된다.
WORKDIR /workspace
COPY backend/src/ backend/src/
COPY frontend/ frontend/
WORKDIR /workspace/backend
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --system groove && useradd --system --gid groove groove
USER groove

COPY --from=builder /workspace/backend/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
