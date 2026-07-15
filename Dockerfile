# syntax=docker/dockerfile:1
#
# SeedStream — deterministic synthetic test data for Kafka, Postgres, and files.
#
# Image contents are OPT-IN. Every path that enters the build context (.dockerignore)
# and every path copied into the image (below) is declared by name. Nothing is
# inherited from the working tree, so the image is a function of the repository —
# not of the machine that happened to build it.
#
# Vendor-neutral: no JDBC driver is baked in — DB jobs mount their driver into
# /work/extras at runtime.
#
# Build:  docker build -t seedstream:latest .
# Run:    docker run --rm -v "$PWD/out:/work/out" seedstream:latest \
#           execute --job config/jobs/quickstart.yaml --count 1000

# ---- build stage ----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# 1. Build definition first, so dependency resolution caches independently of source
#    churn — editing a .java file no longer re-resolves the whole dependency graph.
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY core/build.gradle.kts         core/
COPY schema/build.gradle.kts       schema/
COPY generators/build.gradle.kts   generators/
COPY formats/build.gradle.kts      formats/
COPY destinations/build.gradle.kts destinations/
COPY inspector/build.gradle.kts    inspector/
COPY cli/build.gradle.kts          cli/

# Spotless and SpotBugs resolve these at *configuration* time — the build fails without them.
COPY config/license-header.txt config/spotbugs-exclude.xml config/

# `settings.gradle.kts` includes `benchmarks`, and Gradle refuses to configure a project
# whose directory is missing. The JMH harness is not part of the distribution, so give
# Gradle an empty directory (a project with no build file is a valid empty project)
# rather than copy the module and resolve its plugin graph for nothing.
RUN mkdir -p benchmarks

# 2. Sources. `benchmarks/src` is deliberately absent — see above.
COPY core/src/         core/src/
COPY schema/src/       schema/src/
COPY generators/src/   generators/src/
COPY formats/src/      formats/src/
COPY destinations/src/ destinations/src/
COPY inspector/src/    inspector/src/
COPY cli/src/          cli/src/

RUN ./gradlew :cli:installDist --no-daemon

# 3. Assemble the config the image ships: exactly the jobs the documentation tells
#    users to run, plus the structures those jobs resolve. Benchmark harness jobs
#    (e2e_*, perf_probe_*) and build tooling (spotbugs-exclude.xml, license-header.txt,
#    dependency-check-suppressions.xml) are not user-facing and are not copied.
WORKDIR /dist/config
COPY config/jobs/quickstart.yaml            jobs/
COPY config/jobs/file_invoice.yaml          jobs/
COPY config/jobs/file_invoice_env.yaml      jobs/
COPY config/jobs/file_address.yaml          jobs/
COPY config/jobs/file_customer.yaml         jobs/
COPY config/jobs/file_support_ticket.yaml   jobs/
COPY config/jobs/db_invoice_env.yaml        jobs/
COPY config/jobs/kafka_invoice_env.yaml     jobs/
COPY config/jobs/kafka_events_env_seed.yaml jobs/

COPY config/structures/address.yaml          structures/
COPY config/structures/company.yaml          structures/
COPY config/structures/customer.yaml         structures/
COPY config/structures/event_log.yaml        structures/
COPY config/structures/invoice.yaml          structures/
COPY config/structures/line_item.yaml        structures/
COPY config/structures/support_ticket.yaml   structures/
COPY config/structures/ticket_requester.yaml structures/

COPY config/datafaker-types.example.yaml ./
COPY config/README.md ./

# ---- runtime stage --------------------------------------------------------
FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.title="SeedStream" \
      org.opencontainers.image.description="Deterministic synthetic test data for Kafka, Postgres, and files." \
      org.opencontainers.image.source="https://github.com/mferretti/SeedStream" \
      org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /work

# Application: bin/seedstream + lib/*.jar + extras/ (classpath dir for runtime JDBC drivers)
COPY --from=build /src/cli/build/install/seedstream /opt/seedstream
# Curated sample config — the quickstart runs with zero mounts
COPY --from=build /dist/config /work/config

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

ENTRYPOINT ["seedstream"]
CMD ["--help"]
