# syntax=docker/dockerfile:1

# ---------- Stage 1: build the jar ----------
# A React build stage (node:20) is added here once the frontend exists (Day 7);
# its output is copied into src/main/resources/static before packaging.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

# Download dependencies first so this layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run in CI (build-test job), not inside the image build
RUN mvn -B -q package -DskipTests

# ---------- Stage 2: minimal runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user
RUN groupadd --system lifeledger && useradd --system --gid lifeledger --no-create-home lifeledger

COPY --from=build /workspace/target/lifeledger-*.jar app.jar
RUN chown lifeledger:lifeledger app.jar
USER lifeledger

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:${PORT:-8080}/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
