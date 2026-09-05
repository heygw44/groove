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
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx384m -XX:+UseSerialGC -Duser.timezone=Asia/Seoul"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
