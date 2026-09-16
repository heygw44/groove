# ---- build stage ----
# alpine 태그는 amd64 전용이라 arm64(Apple Silicon) 에서 빌드가 안 된다. 멀티아치 태그를 쓴다.
FROM gradle:8.14-jdk17 AS build
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon -q || true
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ---- run stage ----
FROM eclipse-temurin:17-jre
# uid/gid 를 고정한다. 운영 서버가 /opt/groove/{logs,uploads} 를 같은 소유자로 맞춰야
# 바인드 마운트에 로그와 업로드 파일을 쓸 수 있다.
RUN groupadd -g 10001 groove && useradd -u 10001 -g groove -M -s /usr/sbin/nologin groove \
    && mkdir -p /app/logs /app/uploads && chown -R groove:groove /app
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER groove
# 911MB 호스트에서 비힙(Metaspace 무제한·CodeCache 240M 예약·스레드 스택 1M×200)이
# 커널을 스왑으로 몰아넣는 걸 실측으로 확인했다 — Metaspace·CodeCache·스택에
# 상한을 걸어 비힙 총량을 줄인다. Xmx 는 이번 배포로 노출되는 jvm.memory.used 힙
# 실측을 본 뒤 재조정한다. Xms 를 낮춘 건 SerialGC 가 유휴 시 힙을 되돌려줄 여지를
# 두기 위해서다. 128m 은 운영에서 Metaspace OOM(2026-09-16 22:56)이 났다, 256m 은
# 실사용 상한이 아니라 무한 성장 방지선이다.
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=128m -Xss512k -XX:MaxDirectMemorySize=64m -Duser.timezone=Asia/Seoul"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
