#!/usr/bin/env bash
#
# End-to-End Regex Benchmark Runner
#
# Measures the cost of config-declarable regex types (`regex:` in a --faker-types YAML, backed by
# RgxGen) through the full pipeline: Structure -> Generation -> Serialization -> File.
#
# Two variants are run over an identical matrix:
#   regex     — config/structures/regex_reference.yaml           (4 regex fields + 6 ordinary)
#   baseline  — config/structures/regex_reference_baseline.yaml  (same 10 fields, regex -> char[])
#
# Both use seed 42, so every non-regex field is byte-identical between the two runs. The throughput
# delta is therefore attributable to the regex fields alone. Neither number is meaningful on its own.
#
# This is deliberately a SEPARATE script from run_e2e_test.sh: that harness must keep emitting the
# exact historical 63-scenario matrix for comparability, and its CLI invocation has no --faker-types.
#
# Usage:
#   ./benchmarks/run_regex_e2e.sh [--record-count N] [--warmup-count N]
#
# Outputs:
#   - benchmarks/regex_e2e_results.csv   (raw data)
#   - docs/REGEX-E2E-RESULTS.md          (formatted report)
#

set -euo pipefail

RECORD_COUNT=100000
WARMUP_COUNT=10000

while [[ $# -gt 0 ]]; do
    case $1 in
        --record-count) RECORD_COUNT="$2"; shift 2 ;;
        --warmup-count) WARMUP_COUNT="$2"; shift 2 ;;
        --help) grep '^#' "$0" | head -25; exit 0 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESULTS_FILE="${PROJECT_ROOT}/benchmarks/regex_e2e_results.csv"
REPORT_FILE="${PROJECT_ROOT}/docs/REGEX-E2E-RESULTS.md"
GC_LOG_DIR="${PROJECT_ROOT}/benchmarks/build/gc_logs_regex"
OUTPUT_DIR="${PROJECT_ROOT}/build/run-output"
CLI_SCRIPT="${PROJECT_ROOT}/cli/build/install/seedstream/bin/seedstream"
FAKER_TYPES="${PROJECT_ROOT}/config/datafaker-types.regex-bench.yaml"

# Test matrix — same thread/heap grid as run_e2e_test.sh so the numbers sit alongside it
VARIANTS=("regex" "baseline")
FORMATS=("json" "csv")
THREADS=(1 4 8)
MEMORY_LIMITS=("256m" "512m" "1024m")

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

# --- preflight ---------------------------------------------------------------

if [[ ! -x "$CLI_SCRIPT" ]]; then
    log_error "CLI not found at $CLI_SCRIPT — run: ./gradlew :cli:installDist"
    exit 1
fi
if [[ ! -f "$FAKER_TYPES" ]]; then
    log_error "Faker types file not found: $FAKER_TYPES"
    exit 1
fi

mkdir -p "$GC_LOG_DIR"

# Same GC-log parsing as run_e2e_test.sh — keep in sync if that one changes.
parse_gc_log() {
    local gc_log_file=$1
    if [[ ! -f "$gc_log_file" ]]; then
        echo "0,0,0,0"
        return
    fi
    local gc_data
    gc_data=$(grep -E "Pause.*[0-9\.]+ms" "$gc_log_file" 2>/dev/null || echo "")
    local gc_count
    gc_count=$(echo "$gc_data" | grep -c "Pause" || echo "0")
    local total_gc_time
    total_gc_time=$(echo "$gc_data" | grep -oE "[0-9\.]+ms" | sed 's/ms//' | awk '{sum+=$1} END {printf "%.0f", sum}')
    local heap_data
    heap_data=$(grep -oE "[0-9]+M->[0-9]+M\([0-9]+M\)" "$gc_log_file" 2>/dev/null | tail -1 || echo "0M->0M(0M)")
    local heap_used
    heap_used=$(echo "$heap_data" | grep -oE ">[0-9]+M" | sed 's/[>M]//g' || echo "0")
    local heap_max
    heap_max=$(echo "$heap_data" | grep -oE "\([0-9]+M\)" | sed 's/[()M]//g' || echo "0")
    [[ -z "$total_gc_time" ]] && total_gc_time=0
    [[ -z "$gc_count" ]] && gc_count=0
    [[ -z "$heap_used" ]] && heap_used=0
    [[ -z "$heap_max" ]] && heap_max=0
    echo "${heap_used},${heap_max},${total_gc_time},${gc_count}"
}

run_test() {
    local variant=$1 format=$2 threads=$3 memory=$4
    local memory_mb="${memory%m}"

    local job_file
    if [[ "$variant" == "regex" ]]; then
        job_file="${PROJECT_ROOT}/config/jobs/e2e_regex_file_${format}.yaml"
    else
        job_file="${PROJECT_ROOT}/config/jobs/e2e_regex_baseline_file_${format}.yaml"
    fi

    local gc_log_file="${GC_LOG_DIR}/gc_${variant}_${format}_t${threads}_m${memory}.log"
    local output_file="${GC_LOG_DIR}/output_${variant}_${format}_t${threads}_m${memory}.log"
    local test_name="${variant}/${format}/t${threads}/m${memory}"

    log_info "Running test: $test_name"

    if [[ ! -f "$job_file" ]]; then
        log_warn "Skipping (job file not found): $job_file"
        echo "$variant,$format,$threads,$memory_mb,$RECORD_COUNT,0,0,0,0,0,0,0.0,SKIPPED,Job file not found" >> "$RESULTS_FILE"
        return
    fi

    rm -rf "${OUTPUT_DIR}/e2e_regex_file_${format}" "${OUTPUT_DIR}/e2e_regex_baseline_file_${format}"

    # Warmup — JIT + Datafaker locale load, not measured
    if [[ $WARMUP_COUNT -gt 0 ]]; then
        export JAVA_OPTS="-Xmx${memory} -Xms${memory}"
        log_info "  Warmup: $WARMUP_COUNT records..."
        if ! "$CLI_SCRIPT" execute --job "$job_file" --faker-types "$FAKER_TYPES" \
            --format "$format" --count $WARMUP_COUNT --threads $threads >/dev/null 2>&1; then
            log_warn "  Warmup failed — may indicate memory issue"
        fi
        unset JAVA_OPTS
    fi

    log_info "  Benchmark: $RECORD_COUNT records..."
    local start_time end_time duration throughput status="SUCCESS" error_msg=""
    start_time=$(date +%s%3N)

    export JAVA_OPTS="-Xmx${memory} -Xms${memory} -Xlog:gc*:file=${gc_log_file}:time,level,tags"

    if "$CLI_SCRIPT" execute --job "$job_file" --faker-types "$FAKER_TYPES" \
        --format "$format" --count $RECORD_COUNT --threads $threads >"$output_file" 2>&1; then

        end_time=$(date +%s%3N)
        duration=$((end_time - start_time))
        throughput=0
        [[ $duration -gt 0 ]] && throughput=$((RECORD_COUNT * 1000 / duration))

        local gc_stats heap_used heap_max gc_time gc_count gc_time_percent=0.0
        gc_stats=$(parse_gc_log "$gc_log_file")
        IFS=',' read -r heap_used heap_max gc_time gc_count <<< "$gc_stats"
        if [[ $duration -gt 0 ]]; then
            gc_time_percent=$(awk "BEGIN {printf \"%.2f\", ($gc_time / $duration) * 100}")
        fi

        log_success "  Duration: ${duration}ms | Throughput: ${throughput} rec/s | Heap: ${heap_used}/${heap_max}MB | GC: ${gc_time}ms (${gc_time_percent}%)"
        echo "$variant,$format,$threads,$memory_mb,$RECORD_COUNT,$duration,$throughput,$heap_used,$heap_max,$gc_time,$gc_count,$gc_time_percent,$status,$error_msg" >> "$RESULTS_FILE"
    else
        status="FAILED"
        error_msg="Execution failed"
        if grep -q "OutOfMemoryError" "$output_file" 2>/dev/null; then
            error_msg="OutOfMemoryError"
            log_error "  OutOfMemoryError — insufficient memory"
        elif grep -q "Exception" "$output_file" 2>/dev/null; then
            error_msg=$(grep -m 1 "Exception" "$output_file" | cut -d: -f1 | head -c 50)
            log_error "  Exception: $error_msg"
        else
            log_error "  Test failed (see log: $output_file)"
        fi
        echo "$variant,$format,$threads,$memory_mb,$RECORD_COUNT,0,0,0,0,0,0,0.0,$status,$error_msg" >> "$RESULTS_FILE"
    fi
    unset JAVA_OPTS
}

# --- main --------------------------------------------------------------------

echo "destination,format,threads,memory_mb,record_count,duration_ms,throughput_rps,heap_used_mb,heap_max_mb,gc_time_ms,gc_count,gc_time_percent,status,error" > "$RESULTS_FILE"

TOTAL=$(( ${#VARIANTS[@]} * ${#FORMATS[@]} * ${#THREADS[@]} * ${#MEMORY_LIMITS[@]} ))
log_info "Regex E2E benchmark: $TOTAL scenarios, $RECORD_COUNT records each"
log_info "Started at $(date)"

for variant in "${VARIANTS[@]}"; do
    for format in "${FORMATS[@]}"; do
        for threads in "${THREADS[@]}"; do
            for memory in "${MEMORY_LIMITS[@]}"; do
                run_test "$variant" "$format" "$threads" "$memory"
            done
        done
    done
done

log_success "All scenarios complete. Raw results: $RESULTS_FILE"

# --- report ------------------------------------------------------------------

log_info "Generating report: $REPORT_FILE"

python3 - "$RESULTS_FILE" "$REPORT_FILE" "$RECORD_COUNT" <<'PY'
import csv, sys, datetime
from collections import defaultdict

results_path, report_path, record_count = sys.argv[1], sys.argv[2], int(sys.argv[3])

rows = [r for r in csv.DictReader(open(results_path)) if r['status'] == 'SUCCESS']
by = defaultdict(dict)
for r in rows:
    key = (r['format'], int(r['threads']), int(r['memory_mb']))
    by[key][r['destination']] = r  # 'destination' column holds the variant

def best(variant, fmt=None):
    vals = [int(r['throughput_rps']) for k, v in by.items()
            if variant in v and (fmt is None or k[0] == fmt)]
    return max(vals) if vals else 0

lines = []
lines.append('# Regex Types — End-to-End Results\n')
lines.append(f'**Date:** {datetime.date.today().isoformat()}  ')
lines.append(f'**Records per scenario:** {record_count:,}  ')
lines.append(f'**Scenarios:** {len(rows)} successful\n')
lines.append('Measures config-declarable regex types (`regex:` in a `--faker-types` YAML, backed by')
lines.append('RgxGen) through the full generate → serialize → write pipeline.\n')
lines.append('`regex` uses `regex_reference.yaml` (4 regex fields + 6 ordinary). `baseline` uses')
lines.append('`regex_reference_baseline.yaml` — the same 10 fields with the 4 regex fields replaced by')
lines.append('`char[]` of identical length. Both run under seed 42, so every non-regex field is')
lines.append('byte-identical across the pair; the throughput delta is the regex cost.\n')

lines.append('## Regex vs Baseline\n')
lines.append('| Format | Threads | Heap (MB) | Baseline (rec/s) | Regex (rec/s) | Delta |')
lines.append('|--------|---------|-----------|------------------|---------------|-------|')
for key in sorted(by):
    fmt, threads, mem = key
    v = by[key]
    if 'regex' not in v or 'baseline' not in v:
        continue
    rx = int(v['regex']['throughput_rps'])
    bl = int(v['baseline']['throughput_rps'])
    delta = f'{((rx - bl) / bl * 100):+.1f}%' if bl else 'n/a'
    lines.append(f'| {fmt} | {threads} | {mem} | {bl:,} | {rx:,} | {delta} |')
lines.append('')

lines.append('## Peak Throughput\n')
lines.append('| Variant | Format | Peak (rec/s) |')
lines.append('|---------|--------|--------------|')
for variant in ('baseline', 'regex'):
    for fmt in ('json', 'csv'):
        lines.append(f'| {variant} | {fmt} | {best(variant, fmt):,} |')
lines.append('')

peak_rx, peak_bl = best('regex'), best('baseline')
if peak_bl:
    lines.append(f'**Peak regex throughput:** {peak_rx:,} rec/s vs **{peak_bl:,} rec/s** baseline '
                 f'({(peak_rx - peak_bl) / peak_bl * 100:+.1f}%).\n')

lines.append('## Raw Data\n')
lines.append('See `benchmarks/regex_e2e_results.csv`.\n')

open(report_path, 'w').write('\n'.join(lines))
print(f'Report written: {report_path}')
PY

log_success "Done. Report: $REPORT_FILE"
