#!/usr/bin/env python3
"""
Format JMH benchmark results into a readable markdown report.

Usage:
    python3 format_benchmark_results.py

Reads: benchmarks/build/reports/jmh/results.json
Outputs: BENCHMARK-RESULTS.md
"""

import json
import sys
from pathlib import Path


def main():
    # Locate results file
    results_file = Path('benchmarks/build/reports/jmh/results.json')
    
    if not results_file.exists():
        print(f"ERROR: Results file not found: {results_file}")
        print("Run benchmarks first: ./gradlew :benchmarks:jmh")
        sys.exit(1)
    
    # Load results
    with open(results_file, 'r') as f:
        results = json.load(f)
    
    if not results:
        print("ERROR: No benchmark results found in JSON file")
        sys.exit(1)
    
    print('='*100)
    print('JMH BENCHMARK RESULTS - SeedStream Data Generator')
    print('='*100)
    print()
    
    # Group results by category
    primitives = []
    datafaker = []
    composites = []
    serializers = []
    destinations = []
    
    for result in results:
        benchmark_name = result['benchmark']
        score = result['primaryMetric']['score']
        error = result['primaryMetric']['scoreError']
        
        # Clean up name
        short_name = benchmark_name.split('.')[-1]
        
        if 'PrimitiveGenerators' in benchmark_name:
            primitives.append((short_name, score, error))
        elif 'Datafaker' in benchmark_name:
            datafaker.append((short_name, score, error))
        elif 'Composite' in benchmark_name:
            composites.append((short_name, score, error))
        elif 'Serializer' in benchmark_name:
            serializers.append((short_name, score, error))
        elif 'Destination' in benchmark_name:
            destinations.append((short_name, score, error))
    
    def print_category(title, results_list, target=None):
        print(f"\n{title}")
        print('-' * 100)
        if target:
            print(f"Target: {target}")
            print()
        for name, score, error in sorted(results_list, key=lambda x: x[1], reverse=True):
            print(f"  {name:55s} {score:15,.0f} ops/s  (± {error:,.0f})")
        print()
    
    print_category("PRIMITIVE GENERATORS", primitives, "Target: 10M ops/s (10,000,000)")
    print_category("DATAFAKER GENERATORS (Realistic Data)", datafaker, "Expected: ~10K ops/s")
    print_category("COMPOSITE GENERATORS (Objects & Arrays)", composites)
    print_category("SERIALIZERS (JSON & CSV)", serializers)
    print_category("DESTINATIONS (File I/O)", destinations, "Target: Enable 500 MB/s file writes")
    
    # Summary statistics
    print("\n" + "="*100)
    print("ANALYSIS SUMMARY")
    print("="*100)
    print()
    
    if primitives:
        max_primitive = max(p[1] for p in primitives)
        print(f"✓ Fastest primitive generator: {max_primitive:,.0f} ops/s")
        if max_primitive >= 10_000_000:
            print(f"  ✓ PASSED NFR-1 requirement (10M ops/s)")
        else:
            print(f"  ⚠ BELOW NFR-1 target: {(max_primitive/10_000_000)*100:.1f}% of target")
    
    if datafaker:
        avg_datafaker = sum(d[1] for d in datafaker) / len(datafaker)
        print(f"\n✓ Average Datafaker throughput: {avg_datafaker:,.0f} ops/s")
        print(f"  (Expected: Lower than primitives due to realistic data generation overhead)")
    
    if serializers:
        json_serializers = [s for s in serializers if 'Json' in s[0]]
        if json_serializers:
            avg_json = sum(j[1] for j in json_serializers) / len(json_serializers)
            print(f"\n✓ Average JSON serialization: {avg_json:,.0f} ops/s")
    
    if destinations:
        print(f"\n✓ File I/O benchmarks completed: {len(destinations)} tests")
        print(f"  (Results show raw I/O vs serialization+I/O comparison)")
    
    print("\n" + "="*100)
    print("NOTES")
    print("="*100)
    print("• All measurements in ops/s (operations per second)")
    print("• Higher is better")
    print("• Error margins shown as ± value")
    print("• Hardware: Development machine (specific specs may vary)")
    print("• JMH configuration: 2 warmup iterations, 3 measurement iterations, 1 fork")
    print("="*100)


if __name__ == '__main__':
    main()
