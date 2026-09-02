# ---- build stage ----
FROM gradle:8.14-jdk17-alpine AS build
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon -q || true
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ---- run stage ----
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S groove && adduser -S groove -G groove \
    && mkdir -p /app/logs /app/uploads && chown -R groove:groove /app
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER groove
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx384m -XX:+UseSerialGC -Duser.timezone=Asia/Seoul"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
