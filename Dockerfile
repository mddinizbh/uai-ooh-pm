# ── Stage 1: build ────────────────────────────────────────────────────────────
# Uses the official Maven+Temurin-21 image so no Maven installation is needed.
# Tests are NOT run here — they are gated in the CI test job before this stage.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependency cache layer: invalidated only when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Compile + package (skip tests — CI gate runs them before the image is built)
COPY src ./src
RUN mvn package -Dmaven.test.skip=true -q && \
    cp target/uai-buslines-*.jar target/app.jar

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for least-privilege execution
RUN addgroup -S buslines && adduser -S buslines -G buslines
USER buslines

COPY --from=build /build/target/app.jar app.jar

# All runtime config comes from container env — no secrets baked in (RULE-OPS-01)
# Required: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, UAI_INTERNAL_API_KEY
EXPOSE 8085
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+UseContainerSupport", \
            "-jar", "app.jar"]
