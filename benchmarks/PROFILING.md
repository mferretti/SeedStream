# Performance Profiling Guide

## Quick Start

Enable profiling during E2E benchmarks:

```bash
./benchmarks/run_e2e_test.sh --profile
```

This captures Java Flight Recorder (JFR) data for each test, allowing deep analysis of:
- CPU hotspots (where time is spent)
- Memory allocations (what creates garbage)
- GC behavior (pause times, frequency)
- Thread contention (blocking, waiting)

## Analyzing JFR Files

### Command-Line Analysis

**1. List available profile recordings:**
```bash
ls -lh benchmarks/build/jfr/
```

**2. Show CPU hotspots (top methods consuming CPU):**
```bash
jfr print --events jdk.ExecutionSample \
  benchmarks/build/jfr/profile_file_json_t4_m512m.jfr \
  | grep -A 10 'stackTrace' | head -50
```

**3. Find allocation hotspots (what creates most objects):**
```bash
jfr print --events jdk.ObjectAllocationInNewTLAB \
  benchmarks/build/jfr/profile_file_json_t4_m512m.jfr \
  | grep -E '(eventType|objectClass|stackTrace)' | head -100
```

**4. Analyze GC events:**
```bash
jfr print --events jdk.GarbageCollection \
  benchmarks/build/jfr/profile_file_json_t4_m512m.jfr
```

**5. Check thread dumps for blocking:**
```bash
jfr print --events jdk.JavaMonitorWait \
  benchmarks/build/jfr/profile_file_json_t4_m512m.jfr
```

### GUI Analysis with JDK Mission Control

**Setup:**

JDK Mission Control (JMC) is available with the JDK installation. See the [main README](../README.md#development-environment-setup-recommended-sdkman) for Java/JDK setup instructions using SDKMAN.

**Open a recording:**
```bash
jmc benchmarks/build/jfr/profile_file_json_t4_m512m.jfr
```

**Key views in Mission Control:**
- **Automated Analysis**: Click the lightbulb icon for automatic issue detection
- **Method Profiling**: See flame graph of CPU time by method
- **Memory → Allocations**: See what objects are being created
- **Garbage Collection**: Analyze pause times and frequency
- **Threads**: Find lock contention and blocking

### Flame Graph Visualization

**Convert JFR to flame graph format:**

1. Download JFR-to-FlameGraph converter:
```bash
wget https://github.com/jvm-profiling-tools/jfr-flame-graph/releases/latest/download/jfr-flame-graph
chmod +x jfr-flame-graph
```

2. Generate flame graph:
```bash
./jfr-flame-graph benchmarks/build/jfr/profile_file_json_t4_m512m.jfr > flame.html
```

3. Open in browser:
```bash
firefox flame.html
```

## Common Performance Issues

### High CPU Usage

**Symptom:** CPU samples show same methods repeatedly

**Analysis:**
```bash
jfr print --events jdk.ExecutionSample profile.jfr \
  | awk '/stackTrace/,/^$/ {print}' \
  | grep -oE 'com\.datagenerator\.[^ ]+' \
  | sort | uniq -c | sort -rn | head -20
```

**Solutions:**
- Optimize hot methods (loop unrolling, reduce allocations)
- Use more efficient algorithms
- Consider caching frequently computed values

### Excessive Allocations

**Symptom:** High allocation rate, frequent Young GC

**Analysis:**
```bash
jfr print --events jdk.ObjectAllocationInNewTLAB profile.jfr \
  | grep objectClass | sort | uniq -c | sort -rn | head -20
```

**Solutions:**
- Reuse objects with object pools
- Use primitive arrays instead of wrapper classes
- StringBuilder for string concatenation in loops

### GC Overhead

**Symptom:** >5% time spent in GC

**Analysis:**
```bash
jfr print --events jdk.GarbageCollection profile.jfr
```

**Solutions:**
- Increase heap size if memory available
- Tune GC (G1GC parameters: `-XX:MaxGCPauseMillis`, `-XX:G1HeapRegionSize`)
- Reduce allocation rate (see above)

### Thread Contention

**Symptom:** Tests scale poorly with more threads

**Analysis:**
```bash
jfr print --events jdk.JavaMonitorEnter profile.jfr
jfr print --events jdk.ThreadPark profile.jfr
```

**Solutions:**
- Use lock-free data structures (ConcurrentHashMap, AtomicInteger)
- Reduce critical section size
- Consider thread-local storage

## Benchmarking Best Practices

### 1. Profile Representative Workloads
```bash
# Profile the configuration that matters most
./benchmarks/run_e2e_test.sh --profile

# Then analyze specific scenarios:
# - Highest throughput: file/json/t8/m1024m
# - Production config: kafka/json/t4/m512m
# - Memory constrained: file/csv/t2/m256m
```

### 2. Compare Before/After
```bash
# Baseline
./benchmarks/run_e2e_test.sh --profile
mv benchmarks/build/jfr benchmarks/build/jfr_baseline

# After optimization
./benchmarks/run_e2e_test.sh --profile
mv benchmarks/build/jfr benchmarks/build/jfr_optimized

# Compare allocation rates, CPU hotspots, GC times
```

### 3. Focus on High-Impact Areas

**CPU Profiling Priority:**
1. Methods >5% of total CPU time
2. Methods called >10,000 times
3. Methods in hot loops (generation, serialization)

**Memory Profiling Priority:**
1. Objects >10MB/sec allocation rate
2. Short-lived objects (immediate garbage)
3. Large objects (>100KB)

## Example: End-to-End Performance Investigation

**Scenario:** File/JSON throughput is lower than expected (50K rec/s vs. target 100K rec/s)

**Step 1: Enable profiling**
```bash
./benchmarks/run_e2e_test.sh --profile
```

**Step 2: Identify bottleneck with CPU sampling**
```bash
jfr print --events jdk.ExecutionSample \
  benchmarks/build/jfr/profile_file_json_t4_m512m.jfr \
  | grep -A 5 'stackTrace' > cpu_samples.txt

# Look for:
# - Is it in generation? (com.datagenerator.generators)
# - Is it in serialization? (com.datagenerator.formats)
# - Is it in I/O? (java.nio, java.io)
```

**Step 3: Check allocations**
```bash
jfr print --events jdk.ObjectAllocationInNewTLAB \
  benchmarks/build/jfr/profile_file_json_t4_m512m.jfr \
  | grep objectClass | sort | uniq -c | sort -rn | head -20
```

**Step 4: Optimize based on findings**
- If generation is slow → Profile component benchmarks (./gradlew :generators:jmh)
- If serialization is slow → Check if streaming is used (avoid buffering entire output)
- If I/O is slow → Increase buffer sizes, use direct ByteBuffers

**Step 5: Validate improvement**
```bash
# Re-run with profiling
./benchmarks/run_e2e_test.sh --profile

# Compare throughput in e2e_results.csv
# Compare CPU samples and allocations
```

## Tools Reference

| Tool | Purpose | Command |
|------|---------|---------|
| jfr (CLI) | Built-in JFR analyzer | `jfr print --events <event> file.jfr` |
| JDK Mission Control | GUI profiler | `jmc file.jfr` |
| async-profiler | Flame graphs | `./profiler.sh -d 60 -f flame.html <pid>` |
| jfr-flame-graph | Convert JFR to flame graph | `./jfr-flame-graph file.jfr > flame.html` |
| VisualVM | Alternative GUI | `visualvm --openfile file.jfr` |

## Further Reading

- [JDK Flight Recorder Documentation](https://docs.oracle.com/en/java/javase/21/jfapi/index.html)
- [JFR Event Reference](https://bestsolution-at.github.io/jfr-doc/)
- [Java Performance Best Practices](https://github.com/google/benchmark/blob/main/docs/user_guide.md)
- [Brendan Gregg's USE Method](http://www.brendangregg.com/usemethod.html)
