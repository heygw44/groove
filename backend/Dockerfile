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
RUN groupadd -r groove && useradd -r -g groove groove \
    && mkdir -p /app/logs /app/uploads && chown -R groove:groove /app
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER groove
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx384m -XX:+UseSerialGC -Duser.timezone=Asia/Seoul"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
