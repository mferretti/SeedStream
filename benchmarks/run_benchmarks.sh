#!/bin/bash
#
# Run JMH benchmarks and generate formatted results report
#
# Usage: ./run_benchmarks.sh
#

set -e

echo "=================================================="
echo "SeedStream Performance Benchmarks"
echo "=================================================="
echo ""

# Check if we're in the project root
if [ ! -f "build.gradle.kts" ]; then
    echo "ERROR: Must run from project root directory"
    exit 1
fi

# Run benchmarks
echo "Step 1/2: Running JMH benchmarks (this may take 10-15 minutes)..."
./gradlew :benchmarks:jmh

# Check if benchmark results exist
if [ ! -f "benchmarks/build/reports/jmh/results.json" ]; then
    echo "ERROR: Benchmark results not found. Benchmark run may have failed."
    exit 1
fi

echo ""
echo "Step 2/2: Generating formatted report..."

# Generate report
python3 benchmarks/format_results.py > BENCHMARK-RESULTS.md

echo ""
echo "✓ Benchmarks complete!"
echo "✓ Results saved to: BENCHMARK-RESULTS.md"
echo ""
echo "To view results:"
echo "  cat BENCHMARK-RESULTS.md"
echo ""
