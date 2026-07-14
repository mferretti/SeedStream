# Regex Types — End-to-End Results

**Date:** 2026-07-14  
**Records per scenario:** 100,000  
**Scenarios:** 36 successful

Measures config-declarable regex types (`regex:` in a `--faker-types` YAML, backed by
RgxGen) through the full generate → serialize → write pipeline.

`regex` uses `regex_reference.yaml` (4 regex fields + 6 ordinary). `baseline` uses
`regex_reference_baseline.yaml` — the same 10 fields with the 4 regex fields replaced by
`char[]` of identical length. Both run under seed 42, so every non-regex field is
byte-identical across the pair; the throughput delta is the regex cost.

## Regex vs Baseline

| Format | Threads | Heap (MB) | Baseline (rec/s) | Regex (rec/s) | Delta |
|--------|---------|-----------|------------------|---------------|-------|
| csv | 1 | 256 | 37,721 | 34,141 | -9.5% |
| csv | 1 | 512 | 37,565 | 36,010 | -4.1% |
| csv | 1 | 1024 | 38,328 | 35,460 | -7.5% |
| csv | 4 | 256 | 35,599 | 38,066 | +6.9% |
| csv | 4 | 512 | 40,112 | 36,724 | -8.4% |
| csv | 4 | 1024 | 37,425 | 32,981 | -11.9% |
| csv | 8 | 256 | 36,363 | 34,199 | -6.0% |
| csv | 8 | 512 | 35,460 | 34,423 | -2.9% |
| csv | 8 | 1024 | 33,255 | 33,025 | -0.7% |
| json | 1 | 256 | 35,984 | 34,566 | -3.9% |
| json | 1 | 512 | 34,614 | 32,905 | -4.9% |
| json | 1 | 1024 | 35,880 | 32,435 | -9.6% |
| json | 4 | 256 | 38,167 | 37,023 | -3.0% |
| json | 4 | 512 | 37,425 | 35,650 | -4.7% |
| json | 4 | 1024 | 34,506 | 31,505 | -8.7% |
| json | 8 | 256 | 32,425 | 30,057 | -7.3% |
| json | 8 | 512 | 38,624 | 34,843 | -9.8% |
| json | 8 | 1024 | 38,476 | 34,211 | -11.1% |

## Peak Throughput

| Variant | Format | Peak (rec/s) |
|---------|--------|--------------|
| baseline | json | 33,255 |
| baseline | csv | 33,255 |
| regex | json | 33,255 |
| regex | csv | 33,255 |

**Peak regex throughput:** 33,255 rec/s vs **33,255 rec/s** baseline (+0.0%).

## Raw Data

See `benchmarks/regex_e2e_results.csv`.
