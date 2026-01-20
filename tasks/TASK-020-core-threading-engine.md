# TASK-020: Core Module - Multi-Threading Engine

**Status**: ⏸️ Not Started  
**Priority**: P0 (Critical)  
**Phase**: 5 - CLI & Execution  
**Dependencies**: TASK-007 (Primitive Generators), TASK-008 (Composite Generators)  
**Human Supervision**: MEDIUM (complex threading logic, review concurrent behavior)

---

## Objective

Implement parallel data generation engine that distributes work across multiple threads with deterministic seeding, efficient batching, and backpressure handling. Achieve linear scaling with CPU cores.

---

## Background

Current implementation generates records sequentially in a single thread. For production use cases (millions of records), need parallel generation:
- **16-core machine**: 16x throughput potential
- **Batching**: Amortize serialization and I/O costs
- **Backpressure**: Slow destinations shouldn't cause memory overflow
- **Determinism**: Same seed → same data, even with parallelism

**Key Challenge**: Maintain deterministic output while parallelizing generation.

---

## Architecture Overview

```
Master Thread (coordinates)
    ↓
├─ Worker Thread 0 (workerId=0, seed derived from master)
│   ├─ Generate batch (100 records)
│   ├─ Serialize batch
│   └─ Submit to destination queue
│
├─ Worker Thread 1 (workerId=1, seed derived from master)
│   ├─ Generate batch (100 records)
│   ├─ Serialize batch
│   └─ Submit to destination queue
│
└─ Worker Thread N...

Destination Writer Thread (single thread for ordered writes)
    ├─ Consume from queue
    ├─ Write to destination (file/Kafka/DB)
    └─ Handle backpressure
```

**Key Design Decisions**:
1. **Fixed thread pool**: Size = # of CPU cores (or configurable)
2. **Logical worker IDs**: Sequential (0, 1, 2, ...) for deterministic seeding
3. **Bounded queue**: Backpressure when destination is slow
4. **Single writer thread**: Ensures ordered writes (deterministic output order)

---

## Implementation Details

### Step 1: Create GenerationEngine Class

**File**: `core/src/main/java/com/datagenerator/core/engine/GenerationEngine.java`

**Purpose**: Coordinate parallel generation with deterministic seeding.

```java
package com.datagenerator.core.engine;

import com.datagenerator.core.RandomProvider;
import com.datagenerator.destinations.DestinationAdapter;
import com.datagenerator.formats.FormatSerializer;
import com.datagenerator.generators.ObjectGenerator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Multi-threaded generation engine with deterministic seeding.
 */
@Slf4j
@Builder
public class GenerationEngine {
    
    private final ObjectGenerator generator;
    private final FormatSerializer serializer;
    private final DestinationAdapter destination;
    private final long masterSeed;
    
    @Builder.Default
    private final int workerThreads = Runtime.getRuntime().availableProcessors();
    
    @Builder.Default
    private final int batchSize = 100;
    
    @Builder.Default
    private final int queueCapacity = 1000;
    
    /**
     * Generate specified number of records using parallel workers.
     * 
     * @param count Total number of records to generate
     * @throws InterruptedException if interrupted during generation
     */
    public void generate(long count) throws InterruptedException {
        log.info("Starting generation: {} records using {} workers (batch size: {})", 
            count, workerThreads, batchSize);
        
        // Initialize RandomProvider with master seed
        RandomProvider randomProvider = new RandomProvider(masterSeed);
        
        // Create bounded queue for backpressure
        BlockingQueue<byte[]> recordQueue = new ArrayBlockingQueue<>(queueCapacity);
        
        // Poison pill to signal end of generation
        byte[] POISON_PILL = new byte[0];
        
        // Atomic counter for generated records
        AtomicLong generated = new AtomicLong(0);
        
        // Start worker threads
        ExecutorService workers = Executors.newFixedThreadPool(workerThreads);
        
        // Start writer thread
        Thread writer = new Thread(() -> {
            try {
                while (true) {
                    byte[] record = recordQueue.take();
                    if (record == POISON_PILL) {
                        break;
                    }
                    destination.write(record);
                }
                destination.flush();
                log.info("Writer thread finished");
            } catch (Exception e) {
                log.error("Writer thread failed", e);
                throw new RuntimeException("Writer thread failed", e);
            }
        }, "writer-thread");
        writer.start();
        
        // Submit generation tasks
        long recordsPerWorker = count / workerThreads;
        long remainder = count % workerThreads;
        
        for (int workerId = 0; workerId < workerThreads; workerId++) {
            long workerCount = recordsPerWorker + (workerId < remainder ? 1 : 0);
            int finalWorkerId = workerId;
            
            workers.submit(() -> {
                try {
                    generateWorkerRecords(finalWorkerId, workerCount, randomProvider, 
                        recordQueue, generated, count);
                } catch (Exception e) {
                    log.error("Worker {} failed", finalWorkerId, e);
                    throw new RuntimeException("Worker failed", e);
                }
            });
        }
        
        // Shutdown workers and wait for completion
        workers.shutdown();
        workers.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        
        // Signal writer to stop
        recordQueue.put(POISON_PILL);
        writer.join();
        
        log.info("Generation complete: {} records", generated.get());
    }
    
    private void generateWorkerRecords(int workerId, long count, 
                                       RandomProvider randomProvider,
                                       BlockingQueue<byte[]> queue,
                                       AtomicLong generated,
                                       long totalCount) throws InterruptedException {
        
        // Get thread-local Random for this worker
        var random = randomProvider.getRandom();
        
        long workerGenerated = 0;
        while (workerGenerated < count) {
            // Generate record
            Map<String, Object> record = generator.generate(random, null);
            
            // Serialize record
            byte[] bytes = serializer.serialize(record);
            
            // Submit to queue (blocks if queue is full - backpressure)
            queue.put(bytes);
            
            workerGenerated++;
            long totalGenerated = generated.incrementAndGet();
            
            // Progress logging
            if (totalGenerated % 10000 == 0) {
                double progress = (totalGenerated * 100.0) / totalCount;
                log.info("Progress: {} / {} ({:.1f}%)", totalGenerated, totalCount, progress);
            }
        }
        
        log.debug("Worker {} generated {} records", workerId, workerGenerated);
    }
}
```

---

### Step 2: Integrate with ExecuteCommand

**File**: `cli/src/main/java/com/datagenerator/cli/ExecuteCommand.java`

**Replace sequential generation** with parallel engine:

**Current code** (sequential):
```java
Random random = new Random(seed);
for (int i = 0; i < count; i++) {
    Map<String, Object> record = generator.generate(random, null);
    byte[] bytes = serializer.serialize(record);
    destination.write(bytes);
}
```

**New code** (parallel):
```java
GenerationEngine engine = GenerationEngine.builder()
    .generator(generator)
    .serializer(serializer)
    .destination(destination)
    .masterSeed(seed)
    .workerThreads(Runtime.getRuntime().availableProcessors()) // Or configurable
    .batchSize(100) // Or configurable
    .build();

engine.generate(count);
```

---

### Step 3: Add Configurable Thread Pool Size

**File**: `cli/src/main/java/com/datagenerator/cli/ExecuteCommand.java`

**Add CLI option**:
```java
@Option(
    names = {"--threads", "-t"},
    description = "Number of worker threads (default: # of CPU cores)"
)
private Integer threads;
```

**Use in engine**:
```java
int workerThreads = threads != null ? threads : Runtime.getRuntime().availableProcessors();

GenerationEngine engine = GenerationEngine.builder()
    .generator(generator)
    .serializer(serializer)
    .destination(destination)
    .masterSeed(seed)
    .workerThreads(workerThreads)
    .build();
```

---

### Step 4: Handle Graceful Shutdown

**Challenge**: User presses Ctrl+C during generation → should flush pending records.

**Solution**: Add shutdown hook.

**File**: `cli/src/main/java/com/datagenerator/cli/ExecuteCommand.java`

```java
@Override
public Integer call() {
    // ... existing setup code
    
    // Add shutdown hook for graceful termination
    AtomicBoolean interrupted = new AtomicBoolean(false);
    Thread shutdownHook = new Thread(() -> {
        log.warn("Received shutdown signal, flushing pending records...");
        interrupted.set(true);
    });
    Runtime.getRuntime().addShutdownHook(shutdownHook);
    
    try {
        engine.generate(count);
        return 0;
    } catch (InterruptedException e) {
        log.error("Generation interrupted", e);
        return 2;
    } finally {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
    }
}
```

---

### Step 5: Optimize for Small Record Counts

**Challenge**: For small counts (< 1000), multi-threading overhead > benefit.

**Solution**: Auto-detect and use single thread for small jobs.

**File**: `core/src/main/java/com/datagenerator/core/engine/GenerationEngine.java`

```java
public void generate(long count) throws InterruptedException {
    // For small counts, use single thread (avoid overhead)
    if (count < 1000) {
        log.info("Small job ({}), using single thread", count);
        generateSingleThreaded(count);
        return;
    }
    
    // Otherwise, use multi-threaded approach
    generateMultiThreaded(count);
}

private void generateSingleThreaded(long count) {
    RandomProvider randomProvider = new RandomProvider(masterSeed);
    var random = randomProvider.getRandom();
    
    for (long i = 0; i < count; i++) {
        Map<String, Object> record = generator.generate(random, null);
        byte[] bytes = serializer.serialize(record);
        destination.write(bytes);
        
        if ((i + 1) % 1000 == 0) {
            log.info("Generated {} / {} records", i + 1, count);
        }
    }
    
    destination.flush();
}

private void generateMultiThreaded(long count) throws InterruptedException {
    // ... existing parallel implementation
}
```

---

## Acceptance Criteria

- ✅ GenerationEngine implements parallel generation
- ✅ Uses fixed thread pool (size = # of CPU cores or configurable)
- ✅ Logical worker IDs (0, 1, 2, ...) for deterministic seeding
- ✅ Each worker has thread-local Random instance
- ✅ Bounded queue for backpressure handling
- ✅ Single writer thread for ordered writes
- ✅ Progress logging (every 10k records)
- ✅ Graceful shutdown on Ctrl+C (flushes pending records)
- ✅ Auto-detects small jobs (< 1000) and uses single thread
- ✅ CLI supports --threads option
- ✅ Linear scaling verified (16 threads ≈ 16x throughput)

---

## Testing Requirements

### Unit Tests

**File**: `core/src/test/java/com/datagenerator/core/engine/GenerationEngineTest.java`

**Test cases**:

1. **Test Single-Threaded Generation**:
```java
@Test
void shouldGenerateRecordsInSingleThread() throws Exception {
    // Given: Mock components
    ObjectGenerator generator = mock(ObjectGenerator.class);
    when(generator.generate(any(), any())).thenReturn(Map.of("id", 1));
    
    FormatSerializer serializer = mock(FormatSerializer.class);
    when(serializer.serialize(any())).thenReturn("{}".getBytes());
    
    DestinationAdapter destination = mock(DestinationAdapter.class);
    
    // When: Generate 100 records (single-threaded threshold)
    GenerationEngine engine = GenerationEngine.builder()
        .generator(generator)
        .serializer(serializer)
        .destination(destination)
        .masterSeed(12345L)
        .build();
    
    engine.generate(100);
    
    // Then: Verify calls
    verify(generator, times(100)).generate(any(), any());
    verify(serializer, times(100)).serialize(any());
    verify(destination, times(100)).write(any());
    verify(destination, times(1)).flush();
}
```

2. **Test Multi-Threaded Generation**:
```java
@Test
void shouldGenerateRecordsInParallel() throws Exception {
    // Given: Real components with in-memory destination
    ObjectGenerator generator = new ObjectGenerator(...);
    JsonSerializer serializer = new JsonSerializer();
    InMemoryDestination destination = new InMemoryDestination();
    
    // When: Generate 10,000 records (multi-threaded)
    GenerationEngine engine = GenerationEngine.builder()
        .generator(generator)
        .serializer(serializer)
        .destination(destination)
        .masterSeed(12345L)
        .workerThreads(4)
        .build();
    
    engine.generate(10000);
    
    // Then: Verify record count
    assertThat(destination.getRecordCount()).isEqualTo(10000);
}
```

3. **Test Determinism**:
```java
@Test
void shouldGenerateDeterministicOutput() throws Exception {
    long seed = 12345L;
    
    // Generate twice with same seed
    List<Map<String, Object>> records1 = generateRecords(seed, 1000);
    List<Map<String, Object>> records2 = generateRecords(seed, 1000);
    
    // Verify identical output
    assertThat(records1).isEqualTo(records2);
}

private List<Map<String, Object>> generateRecords(long seed, int count) throws Exception {
    InMemoryDestination destination = new InMemoryDestination();
    GenerationEngine engine = GenerationEngine.builder()
        .generator(generator)
        .serializer(serializer)
        .destination(destination)
        .masterSeed(seed)
        .workerThreads(4)
        .build();
    
    engine.generate(count);
    return destination.getRecords();
}
```

4. **Test Backpressure**:
```java
@Test
void shouldHandleSlowDestination() throws Exception {
    // Given: Slow destination (100ms per write)
    SlowDestination destination = new SlowDestination(100);
    
    // When: Generate records
    GenerationEngine engine = GenerationEngine.builder()
        .generator(generator)
        .serializer(serializer)
        .destination(destination)
        .masterSeed(12345L)
        .workerThreads(4)
        .queueCapacity(10) // Small queue to test backpressure
        .build();
    
    // Should complete without OutOfMemoryError
    engine.generate(100);
    
    assertThat(destination.getRecordCount()).isEqualTo(100);
}
```

5. **Test Thread Count Configuration**:
```java
@Test
void shouldUseConfiguredThreadCount() throws Exception {
    GenerationEngine engine = GenerationEngine.builder()
        .generator(generator)
        .serializer(serializer)
        .destination(destination)
        .masterSeed(12345L)
        .workerThreads(8)
        .build();
    
    // Verify 8 threads used (via internal monitoring)
    engine.generate(10000);
    
    // This is hard to verify directly, but can be checked via logging or instrumentation
}
```

**Minimum**: 5 unit tests (as above)

---

### Performance Tests

**File**: `core/src/test/java/com/datagenerator/core/engine/GenerationEnginePerformanceTest.java`

**Verify linear scaling**:

```java
@Test
void shouldScaleLinearlyWithThreads() throws Exception {
    int recordCount = 100_000;
    
    // Benchmark with 1, 2, 4, 8 threads
    long time1 = benchmark(1, recordCount);
    long time2 = benchmark(2, recordCount);
    long time4 = benchmark(4, recordCount);
    long time8 = benchmark(8, recordCount);
    
    // Verify near-linear scaling (within 20% tolerance)
    double speedup2 = (double) time1 / time2;
    double speedup4 = (double) time1 / time4;
    double speedup8 = (double) time1 / time8;
    
    assertThat(speedup2).isBetween(1.6, 2.4); // 1.6x to 2.4x (ideal: 2x)
    assertThat(speedup4).isBetween(3.2, 4.8); // 3.2x to 4.8x (ideal: 4x)
    assertThat(speedup8).isBetween(6.4, 9.6); // 6.4x to 9.6x (ideal: 8x)
    
    log.info("Speedup - 2 threads: {:.2f}x, 4 threads: {:.2f}x, 8 threads: {:.2f}x",
        speedup2, speedup4, speedup8);
}

private long benchmark(int threads, int count) throws Exception {
    InMemoryDestination destination = new InMemoryDestination();
    GenerationEngine engine = GenerationEngine.builder()
        .generator(generator)
        .serializer(serializer)
        .destination(destination)
        .masterSeed(12345L)
        .workerThreads(threads)
        .build();
    
    long start = System.currentTimeMillis();
    engine.generate(count);
    long end = System.currentTimeMillis();
    
    return end - start;
}
```

---

## Files Created

- `core/src/main/java/com/datagenerator/core/engine/GenerationEngine.java`
- `core/src/test/java/com/datagenerator/core/engine/GenerationEngineTest.java`
- `core/src/test/java/com/datagenerator/core/engine/GenerationEnginePerformanceTest.java`
- `core/src/test/java/com/datagenerator/core/engine/InMemoryDestination.java` (test utility)
- `core/src/test/java/com/datagenerator/core/engine/SlowDestination.java` (test utility)

---

## Files Modified

- `cli/src/main/java/com/datagenerator/cli/ExecuteCommand.java` (use GenerationEngine)

---

## Common Issues & Solutions

**Issue**: Slower with multiple threads than single thread  
**Solution**: Verify record count is large enough (> 1000). Check for synchronization bottlenecks.

**Issue**: Non-deterministic output  
**Solution**: Verify logical worker IDs are sequential (0, 1, 2, ...), not JVM thread IDs

**Issue**: OutOfMemoryError with large jobs  
**Solution**: Reduce queue capacity or batch size to apply backpressure earlier

**Issue**: Destination writes are slow  
**Solution**: Increase batch size or reduce worker threads (fewer workers = less contention)

**Issue**: Progress logging too verbose  
**Solution**: Increase logging threshold (e.g., every 100k records instead of 10k)

---

## Completion Checklist

- [ ] GenerationEngine class implemented
- [ ] Single-threaded fallback for small jobs (< 1000)
- [ ] Multi-threaded generation with worker pool
- [ ] Bounded queue for backpressure
- [ ] Single writer thread for ordered writes
- [ ] Logical worker IDs (0, 1, 2, ...)
- [ ] Progress logging implemented
- [ ] Graceful shutdown on Ctrl+C
- [ ] CLI --threads option added
- [ ] Unit tests pass (5 tests)
- [ ] Performance test shows linear scaling
- [ ] Build succeeds: `./gradlew :core:build`
- [ ] Integration with CLI complete
- [ ] Determinism verified (same seed → same output with multiple threads)

---

**Estimated Effort**: 8-10 hours  
**Complexity**: High (threading, synchronization, performance tuning)  
**Human Supervision**: MEDIUM (review concurrent behavior, verify determinism)
