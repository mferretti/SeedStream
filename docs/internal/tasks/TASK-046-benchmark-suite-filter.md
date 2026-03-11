# TASK-046: Benchmark Suite Filter (`jmhSuite` property)

**Status:** Complete ✅
**Priority:** P2
**Phase:** Phase 8 (Database Destinations)
**Completed:** 2026-03-10

---

## Goal

Allow developers to run a single benchmark suite (database, kafka, or generators) without
triggering all suites, and without needing to memorise JMH class-name regex patterns.

---

## Problem

`me.champeau.jmh` plugin 0.7.x: an explicit `includes.set(...)` call in the `jmh {}` block
**overrides** the `-Pjmh.includes` command-line property — the Gradle property arrives too late to
compete with the configuration-phase assignment. The benchmarks module previously contained:

```kotlin
includes.set(listOf(".*"))   // unconditional — silently overrides any -Pjmh.includes
```

This meant `./gradlew :benchmarks:jmh -Pjmh.includes=".*DatabaseBenchmark.*"` still ran
every benchmark class.

---

## Solution Implemented

**File:** `benchmarks/build.gradle.kts`

Replaced the unconditional `includes.set(listOf(".*"))` with a conditional block guarded by a
`jmhSuite` project property:

```kotlin
if (project.hasProperty("jmhSuite")) {
    val suite = project.property("jmhSuite") as String
    val pattern = when (suite) {
        "database"   -> listOf(".*DatabaseBenchmark.*")
        "kafka"      -> listOf(".*KafkaBenchmark.*")
        "generators" -> listOf(".*(PrimitiveGenerators|DatafakerGenerators|CompositeGenerators|Serializer|Destination)Benchmark.*")
        else         -> throw GradleException("Unknown jmhSuite '$suite'. Valid values: database, kafka, generators")
    }
    includes.set(pattern)
}
// Do NOT set includes unconditionally here — me.champeau.jmh 0.7.x: an explicit
// includes.set(...) overrides -Pjmh.includes from the command line.
```

When `jmhSuite` is absent, no `includes` filter is set and JMH runs all benchmarks (default
JMH behaviour — match everything).

---

## Usage

```bash
# Run only database benchmarks
./gradlew :benchmarks:jmh -PjmhSuite=database

# Run only Kafka benchmarks
./gradlew :benchmarks:jmh -PjmhSuite=kafka

# Run only generator/serializer benchmarks
./gradlew :benchmarks:jmh -PjmhSuite=generators

# Run all benchmarks
./gradlew :benchmarks:jmh

# Fine-grained filter still works (no jmhSuite set)
./gradlew :benchmarks:jmh -Pjmh.includes=".*benchmarkFlatInsert.*"
```

---

## Notes

- `-Pjmh.includes` (fine-grained) and `-PjmhSuite` (coarse-grained) are mutually exclusive.
  Do not set both — `jmhSuite` will win because it sets `includes` at configuration phase.
- The `generators` suite pattern covers all non-infrastructure benchmark classes. Add new
  benchmark class names to the pattern when new generator benchmarks are introduced.
