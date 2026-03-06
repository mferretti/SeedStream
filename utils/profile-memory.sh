#!/bin/bash
# Memory profiling script using Java Flight Recorder (JFR)
#
# Usage: ./profile-memory.sh <job-file> <record-count> [threads]
#
# Example: ./profile-memory.sh config/jobs/kafka_address.yaml 1000000 4
#
# This script runs data generation with JFR enabled and generates a memory profile report.

set -e

if [ $# -lt 2 ]; then
    echo "Usage: $0 <job-file> <record-count> [threads]"
    echo ""
    echo "Examples:"
    echo "  $0 config/jobs/kafka_address.yaml 1000000"
    echo "  $0 config/jobs/kafka_address.yaml 10000000 8"
    exit 1
fi

JOB_FILE="$1"
RECORD_COUNT="$2"
THREADS="${3:-4}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RECORDING_FILE="memory-profile-${TIMESTAMP}.jfr"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/profiling-output"

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo "=== Memory Profiling Configuration ==="
echo "Job file: $JOB_FILE"
echo "Record count: $RECORD_COUNT"
echo "Threads: $THREADS"
echo "Recording file: $OUTPUT_DIR/$RECORDING_FILE"
echo ""

# JFR settings
JFR_SETTINGS="-XX:StartFlightRecording=filename=$OUTPUT_DIR/$RECORDING_FILE,settings=profile"
JVM_OPTS="-Xms512m -Xmx4g -XX:+UseG1GC -Xlog:gc*:file=$OUTPUT_DIR/gc-${TIMESTAMP}.log"

echo "Starting data generation with JFR profiling..."
echo ""

# Build CLI distribution if needed
if [ ! -f "cli/build/install/cli/bin/cli" ]; then
    echo "Building CLI distribution..."
    ./gradlew :cli:installDist
    echo ""
fi

# Convert job file to absolute path
if [[ ! "$JOB_FILE" = /* ]]; then
    JOB_FILE="$(pwd)/$JOB_FILE"
fi

# Run the CLI with JFR enabled
JAVA_OPTS="$JVM_OPTS $JFR_SETTINGS" cli/build/install/cli/bin/cli execute --job "$JOB_FILE" --count $RECORD_COUNT --threads $THREADS

echo ""
echo "=== Profiling Complete ==="
echo "JFR recording saved to: $OUTPUT_DIR/$RECORDING_FILE"
echo "GC log saved to: $OUTPUT_DIR/gc-${TIMESTAMP}.log"
echo ""
echo "To view the JFR file:"
echo "  1. Open with JDK Mission Control: jmc $OUTPUT_DIR/$RECORDING_FILE"
echo "  2. Or view with jfr tool:"
echo "     jfr print --events jdk.ObjectAllocationInNewTLAB $OUTPUT_DIR/$RECORDING_FILE"
echo "     jfr print --events jdk.GarbageCollection $OUTPUT_DIR/$RECORDING_FILE"
echo ""
echo "To analyze GC log:"
echo "  cat $OUTPUT_DIR/gc-${TIMESTAMP}.log | grep -E 'Pause|Collection'"
echo ""

# Generate basic summary if jfr command is available
if command -v jfr &> /dev/null; then
    echo "=== JFR Summary ==="
    echo ""
    echo "Top Memory Allocations:"
    jfr print --events jdk.ObjectAllocationInNewTLAB "$OUTPUT_DIR/$RECORDING_FILE" 2>/dev/null | \
        grep -A 5 "ObjectAllocationInNewTLAB" | head -20 || echo "(Use jmc for detailed analysis)"
    echo ""
fi

echo "Profiling data saved in: $OUTPUT_DIR/"
