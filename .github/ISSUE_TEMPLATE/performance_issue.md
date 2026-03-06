---
name: Performance Issue
about: Report performance problems or request optimization
title: '[PERFORMANCE] '
labels: performance
assignees: ''
---

## Performance Issue

Describe the performance problem you're experiencing.

## Current Behavior

- **Throughput**: (e.g., 1,000 records/sec)
- **Data Type**: (e.g., Primitive, Datafaker, Nested objects)
- **Destination**: (e.g., File, Kafka)
- **Format**: (e.g., JSON, CSV)
- **Worker Threads**: (e.g., `--threads 8`)

## Expected Performance

What throughput or latency were you expecting based on docs or benchmarks?

Reference: [docs/PERFORMANCE.md](https://github.com/mferretti/SeedStream/blob/main/docs/PERFORMANCE.md)

## Benchmark Command

```bash
# Show the exact command you ran
./gradlew :cli:run --args="execute --job config/jobs/..."
```

## Configuration

<details>
<summary>Job & Structure (click to expand)</summary>

```yaml
# Paste relevant configuration
```

</details>

## Hardware

- **CPU**: (e.g., Intel i7-12700K, 12 cores)
- **RAM**: (e.g., 32 GB)
- **Disk**: (e.g., NVMe SSD 500 MB/s write)
- **OS**: (e.g., Ubuntu 22.04)
- **Java Version**: (run `java -version`)

## Profiling

If you've done profiling (JFR, VisualVM, etc.), share findings:
- CPU hot spots
- Memory allocation patterns
- I/O bottlenecks

## Suggestions

Any ideas for optimization?
