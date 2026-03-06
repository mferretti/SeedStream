#!/bin/bash
# Hardware I/O Performance Test
# Tests raw disk write throughput to establish hardware ceiling

set -e

echo "======================================================================================================"
echo "HARDWARE I/O PERFORMANCE TEST"
echo "======================================================================================================"
echo ""

# Test configuration
TEST_FILE="./benchmark_io_test.dat"
BLOCK_SIZE="1M"     # 1 MB blocks
BLOCK_COUNT=1024    # 1 GB total

echo "Configuration:"
echo "  Test File: $TEST_FILE"
echo "  Block Size: $BLOCK_SIZE"
echo "  Total Size: ${BLOCK_COUNT}MB (1 GB)"
echo ""

# Cleanup any existing test file
rm -f "$TEST_FILE"

echo "======================================================================================================"
echo "TEST 1: Sequential Write (No Cache)"
echo "======================================================================================================"
echo ""
echo "Running: dd if=/dev/zero of=$TEST_FILE bs=$BLOCK_SIZE count=$BLOCK_COUNT conv=fdatasync"
echo ""

dd if=/dev/zero of="$TEST_FILE" bs="$BLOCK_SIZE" count=$BLOCK_COUNT conv=fdatasync 2>&1 | tee /tmp/dd_output.txt

# Extract throughput
WRITE_SPEED=$(grep -oP '\d+(\.\d+)? MB/s' /tmp/dd_output.txt || echo "N/A")
echo ""
echo "Result: $WRITE_SPEED"
echo ""

# Cleanup
rm -f "$TEST_FILE"

echo "======================================================================================================"
echo "TEST 2: Buffered Write (System Cache)"
echo "======================================================================================================"
echo ""
echo "Running: dd if=/dev/zero of=$TEST_FILE bs=$BLOCK_SIZE count=$BLOCK_COUNT"
echo ""

dd if=/dev/zero of="$TEST_FILE" bs="$BLOCK_SIZE" count=$BLOCK_COUNT 2>&1 | tee /tmp/dd_output2.txt

BUFFERED_SPEED=$(grep -oP '\d+(\.\d+)? MB/s' /tmp/dd_output2.txt || echo "N/A")
echo ""
echo "Result: $BUFFERED_SPEED"
echo ""

# Cleanup
rm -f "$TEST_FILE"
rm -f /tmp/dd_output.txt /tmp/dd_output2.txt

echo "======================================================================================================"
echo "TEST 3: Java NIO Buffered Write"
echo "======================================================================================================"
echo ""

# Create a simple Java program to test NIO performance
cat > /tmp/JavaIOTest.java << 'EOF'
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class JavaIOTest {
    public static void main(String[] args) throws IOException {
        Path testFile = Paths.get("./benchmark_java_io_test.dat");
        
        // Generate a sample JSON record (~280 bytes)
        String jsonRecord = "{\"id\":12345,\"name\":\"John Doe Smith Jr.\",\"email\":\"john.doe.smith@example.com\",\"phone\":\"+1-555-123-4567\",\"address\":\"123 Main Street, Apartment 4B, Building C\",\"city\":\"New York City\",\"company\":\"Tech Solutions International Inc.\",\"birthDate\":\"1990-05-15\",\"createdAt\":\"2024-03-15T10:30:00Z\"}\n";
        
        int recordsToWrite = 1_000_000; // 1 million records
        long totalBytes = jsonRecord.length() * recordsToWrite;
        
        System.out.println("Test configuration:");
        System.out.println("  Records: " + recordsToWrite);
        System.out.println("  Record size: " + jsonRecord.length() + " bytes");
        System.out.println("  Total size: " + (totalBytes / 1024 / 1024) + " MB");
        System.out.println("");
        
        // Test with BufferedWriter (8KB buffer - default)
        System.out.println("Test: BufferedWriter with 8KB buffer");
        long start = System.nanoTime();
        try (BufferedWriter writer = Files.newBufferedWriter(testFile, StandardCharsets.UTF_8, 
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            for (int i = 0; i < recordsToWrite; i++) {
                writer.write(jsonRecord);
            }
        }
        long duration = System.nanoTime() - start;
        double seconds = duration / 1_000_000_000.0;
        double throughputMBps = (totalBytes / 1024.0 / 1024.0) / seconds;
        double throughputRecords = recordsToWrite / seconds;
        
        System.out.println("  Duration: " + String.format("%.2f", seconds) + " seconds");
        System.out.println("  Throughput: " + String.format("%.2f", throughputMBps) + " MB/s");
        System.out.println("  Throughput: " + String.format("%.0f", throughputRecords) + " records/s");
        System.out.println("");
        
        Files.deleteIfExists(testFile);
        
        // Test with larger buffer (64KB)
        System.out.println("Test: BufferedWriter with 64KB buffer");
        start = System.nanoTime();
        try (BufferedWriter writer = Files.newBufferedWriter(testFile, StandardCharsets.UTF_8, 
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            // Manually set larger buffer
            writer = new BufferedWriter(writer, 65536);
            for (int i = 0; i < recordsToWrite; i++) {
                writer.write(jsonRecord);
            }
        }
        duration = System.nanoTime() - start;
        seconds = duration / 1_000_000_000.0;
        throughputMBps = (totalBytes / 1024.0 / 1024.0) / seconds;
        throughputRecords = recordsToWrite / seconds;
        
        System.out.println("  Duration: " + String.format("%.2f", seconds) + " seconds");
        System.out.println("  Throughput: " + String.format("%.2f", throughputMBps) + " MB/s");
        System.out.println("  Throughput: " + String.format("%.0f", throughputRecords) + " records/s");
        System.out.println("");
        
        Files.deleteIfExists(testFile);
    }
}
EOF

# Compile and run
javac /tmp/JavaIOTest.java
java -cp /tmp JavaIOTest
rm -f /tmp/JavaIOTest.java /tmp/JavaIOTest.class
rm -f ./benchmark_java_io_test.dat

echo "======================================================================================================"
echo "SUMMARY"
echo "======================================================================================================"
echo ""
echo "Hardware Limits (dd sequential write):"
echo "  No cache (fdatasync):  $WRITE_SPEED"
echo "  Buffered (system cache): $BUFFERED_SPEED"
echo ""
echo "Java NIO Performance: See results above"
echo ""
echo "Compare these results against:"
echo "  - Current JMH benchmark: 761,076 records/s (FileDestination)"
echo "  - Target requirement: 500 MB/s"
echo ""
echo "======================================================================================================"
