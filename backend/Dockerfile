# Multi-stage build for the Spring Boot backend.
#
# JDK 21 is mandatory: the project uses Lombok, and building on a newer JDK (e.g. 26)
# fails with "Fatal error compiling: ... TypeTag :: UNKNOWN". Pinning the build image to
# eclipse-temurin-21 makes the build reproducible regardless of the host JDK.

# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first and pre-fetch dependencies so this layer is cached and only
# re-runs when pom.xml actually changes (source edits don't bust the dependency cache).
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Now copy sources and build the executable jar. Tests run in CI / `mvnw verify`,
# not in the image build (Testcontainers needs a Docker daemon — skip here).
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# curl is needed by the container HEALTHCHECK (and the compose healthcheck) to probe
# the Actuator endpoint; the slim JRE image doesn't ship it. Clean apt lists to keep
# the layer small.
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root user — never run the app as root in a container.
RUN useradd --system --no-create-home --uid 10001 appuser
USER appuser

# The fat jar is self-contained; copy it as a stable name. (Spring Boot layered-jar
# extraction would improve image-layer caching, but KISS: a single fat jar is enough here.)
COPY --from=build /build/target/mini-shop-backend-*.jar /app/app.jar

EXPOSE 8080

# Liveness/readiness for orchestrators and compose `depends_on: service_healthy`.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=10 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
