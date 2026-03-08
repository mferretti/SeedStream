# Historical Benchmark Analysis

This directory contains historical performance analysis documents from the benchmark development and optimization process.

**These documents are kept for reference and learning, but are NOT the current performance status.**

---

## Current Performance Status

For **up-to-date performance metrics and analysis**, see:
- [../../PERFORMANCE-STATUS.md](../../PERFORMANCE-STATUS.md) - **Current consolidated status**
- [../../E2E-TEST-RESULTS.md](../../E2E-TEST-RESULTS.md) - Latest E2E test results
- [../../BENCHMARK-RESULTS.md](../../BENCHMARK-RESULTS.md) - Latest JMH component results
- [../../PERFORMANCE.md](../../PERFORMANCE.md) - Performance guide and tuning

---

## Historical Documents

### Baseline Analysis & Root Cause (March 5-7, 2026)

**[BASELINE-PERFORMANCE-FINDINGS.md](BASELINE-PERFORMANCE-FINDINGS.md)**
- **Consolidated analysis** of pre-optimization baseline (7-20K rec/s)
- CPU profiling results (98.1% in Datafaker operations)
- Root cause: Improper Faker usage defeating internal caching (800K instantiations)
- Thread efficiency analysis (32% before fix)
- Mathematical projections and validation
- Combines: ANALYSIS-SUMMARY + HOTSPOTS-SUMMARY

### Optimization Implementation (March 7-8, 2026)

**[THREAD-LOCAL-OPTIMIZATION-STRATEGY.md](THREAD-LOCAL-OPTIMIZATION-STRATEGY.md)**
- Complete thread-local Faker cache strategy
- Implementation details and code
- Expected vs actual results (3.5-5× improvement achieved)
- Post-optimization validation

### Specialized Benchmarks (March 6-7, 2026)

**[PERFORMANCE-ANALYSIS.md](PERFORMANCE-ANALYSIS.md)**
- File I/O performance deep-dive
- Hardware baseline tests (dd, Java NIO)
- Buffer size optimization analysis

**[SERIALIZER-BENCHMARK-RESULTS.md](SERIALIZER-BENCHMARK-RESULTS.md)**
- JSON/CSV serializer component benchmarks
- Jackson ObjectMapper performance
- Serialization overhead analysis

**[KAFKA-BENCHMARK-RESULTS.md](KAFKA-BENCHMARK-RESULTS.md)**
- Kafka producer performance analysis
- Async vs sync comparison
- Compression and batching impact

---

## Key Lessons Learned

### 1. Verify Library Usage Before Blaming Performance

**Initial Hypothesis:** Datafaker YAML parsing is slow (98% CPU time in Datafaker)

**Reality:** We defeated Datafaker's internal caching by creating new instances for every value (800K instantiations per 100K test). Datafaker HAS caching - we just kept resetting it.

**Lesson:** Always verify you're using a library correctly before assuming it's the bottleneck.

### 2. Component Benchmarks Don't Predict E2E Performance

**Isolated Datafaker:** 17-154K ops/s  
**E2E Pipeline (pre-optimization):** 7K rec/s (much slower)

**Gap was caused by:** Instance creation overhead in hot loop, not Datafaker itself being slow.

### 3. Profile Before Optimizing

JFR profiling was critical to identifying the real bottleneck. Without profiling, we might have:
- Optimized the wrong thing (e.g., serializers at 0.2% CPU)
- Missed the improper usage pattern (creating new Faker instances)
- Wasted time on micro-optimizations with minimal impact

### 4. Thread Efficiency Reveals Hidden Bottlenecks

**Pre-optimization:** 32% thread efficiency (2.86× speedup at 8 threads vs expected 8×)  
**Post-optimization:** 60-70% thread efficiency

The poor scaling was a symptom of the real problem: CPU saturation from repeated Faker instantiation.

---

## Timeline

| Date | Event | Key Metric |
|------|-------|------------|
| March 5, 2026 | Initial E2E baseline | 7K rec/s (1T), 18K rec/s (8T) |
| March 6, 2026 | Hardware & component benchmarks | Validated Datafaker: 17-154K ops/s isolated |
| March 7, 2026 | JFR profiling & root cause analysis | Identified: 98% CPU in Datafaker (but due to our misuse) |
| March 8, 2026 | Thread-local Faker cache implemented | **3.5-5× improvement: 25-33K rec/s** |
| March 8, 2026 | Documentation consolidation | Created PERFORMANCE-STATUS.md |

---

## Document Retention Policy

These historical documents are retained for:
1. **Learning reference** - Understanding how bottlenecks were identified and resolved
2. **Methodology examples** - Profiling techniques, analysis approach
3. **Audit trail** - Historical context for performance decisions

**Do NOT use these for current performance numbers.** Always reference the current documentation in `../../` directory.

---

**Questions about historical analysis?** See [../../CONTRIBUTING.md](../../CONTRIBUTING.md) or open an issue.
