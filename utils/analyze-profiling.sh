#!/bin/bash
# Quick profiling analysis script

if [ $# -lt 1 ]; then
    echo "Usage: $0 <timestamp>"
    echo "Example: $0 20260306_150743"
    exit 1
fi

TIMESTAMP=$1
JFR_FILE="profiling-output/memory-profile-${TIMESTAMP}.jfr"
GC_LOG="profiling-output/gc-${TIMESTAMP}.log"

echo "=== Profiling Analysis for $TIMESTAMP ==="
echo ""

if [ ! -f "$GC_LOG" ]; then
    echo "GC log not found: $GC_LOG"
    exit 1
fi

echo "GC Log Analysis:"
echo "----------------"
GC_COUNT=$(grep -c "GC(" "$GC_LOG" | head -1)
echo "Total GC cycles: $(( $GC_COUNT / 2 ))"

echo ""
echo "Last 5 GC events:"
grep "Pause Young" "$GC_LOG" | tail -5

echo ""
echo "Final heap state:"
tail -10 "$GC_LOG" | grep -A2 "heap,exit"

echo ""
echo "JFR Summary:"
echo "------------"
if [ -f "$JFR_FILE" ]; then
    jfr summary "$JFR_FILE" 2>/dev/null | head -10
else
    echo "JFR file not found: $JFR_FILE"
fi
