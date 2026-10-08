# ---------- Stage 1: build the executable jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Gradle wrapper + build scripts first, so the dependency layer is cached between builds
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
 && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---------- Stage 2: small runtime image ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system campus && useradd --system --gid campus campus
COPY --from=build /workspace/build/libs/*.jar app.jar
USER campus
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
