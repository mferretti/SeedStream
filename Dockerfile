# syntax=docker/dockerfile:1
#
# SeedStream — deterministic synthetic test data for Kafka, Postgres, and files.
# Multi-stage build (Option A: installDist + JRE). Vendor-neutral: no JDBC driver
# is baked in — DB jobs mount their driver into /work/extras at runtime.
#
# Build:  docker build -t seedstream:latest .
# Run:    docker run --rm -v "$PWD/out:/work/out" seedstream:latest \
#           execute --job config/jobs/quickstart.yaml --count 1000

# ---- build stage ----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :cli:installDist --no-daemon

# ---- runtime stage --------------------------------------------------------
FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.title="SeedStream" \
      org.opencontainers.image.description="Deterministic synthetic test data for Kafka, Postgres, and files." \
      org.opencontainers.image.source="https://github.com/mferretti/SeedStream" \
      org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /work

# Application: bin/cli + lib/*.jar + extras/ (classpath dir for runtime JDBC drivers)
COPY --from=build /src/cli/build/install/cli /opt/seedstream
# Baked-in sample config so the quickstart runs with zero mounts
COPY --from=build /src/config /work/config

ENV PATH="/opt/seedstream/bin:${PATH}"
# Container-aware heap: adapt to the cgroup memory limit instead of a fixed -Xmx.
# Threads default to the cgroup-visible CPU count (Runtime.availableProcessors,
# container-aware in Java 21) — size the container to tune throughput.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# Non-root; only /work/out needs to be writable.
RUN useradd -r -u 10001 seed \
    && mkdir -p /work/out /work/extras \
    && chown -R seed /work
USER seed

ENTRYPOINT ["cli"]
CMD ["--help"]
