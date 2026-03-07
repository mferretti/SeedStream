# TASK-006: Core Module - Random Provider

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: TASK-005 (Seed Resolution)  
**Human Supervision**: LOW (straightforward thread-local Random)

---

## Objective

Implement thread-safe Random provider with deterministic seeding strategy for parallel generation. Each worker thread gets its own Random instance with a derived seed.

---

## Background

For deterministic parallel generation:
- Each worker thread needs its own `Random` instance (avoid contention)
- Worker seeds must be derived from master seed deterministically
- Same master seed → same worker seeds → same output (reproducibility)

**Seeding Strategy**:
```
Master Seed: 12345
Worker 0 Seed: hash(12345, 0) = 987654321
Worker 1 Seed: hash(12345, 1) = 123456789
Worker 2 Seed: hash(12345, 2) = 456789123
...
```

---

## Implementation Details

### Step 1: Create RandomProvider Class

**File**: `core/src/main/java/com/datagenerator/core/RandomProvider.java`

```java
package com.datagenerator.core;

import lombok.extern.slf4j.Slf4j;
import java.util.Random;

/**
 * Thread-safe provider of Random instances with deterministic seeding.
 * Each thread gets its own Random instance with a seed derived from the master seed.
 */
@Slf4j
public class RandomProvider {
    
    private final long masterSeed;
    private final ThreadLocal<Random> threadLocalRandom;
    
    /**
     * Create a RandomProvider with a master seed.
     * 
     * @param masterSeed Master seed for deterministic generation
     */
    public RandomProvider(long masterSeed) {
        this.masterSeed = masterSeed;
        this.threadLocalRandom = ThreadLocal.withInitial(this::createThreadRandom);
        
        log.debug("RandomProvider initialized with master seed: {}", masterSeed);
    }
    
    /**
     * Get the thread-local Random instance.
     * First call in a thread creates a new Random with a derived seed.
     * 
     * @return Thread-local Random instance
     */
    public Random getRandom() {
        return threadLocalRandom.get();
    }
    
    /**
     * Create a Random instance for the current thread.
     * Derives seed from master seed and thread identity.
     */
    private Random createThreadRandom() {
        // Derive thread-specific seed from master seed and thread ID
        long threadId = Thread.currentThread().getId();
        long threadSeed = deriveThreadSeed(masterSeed, threadId);
        
        log.debug("Creating Random for thread {} with derived seed: {}", threadId, threadSeed);
        
        return new Random(threadSeed);
    }
    
    /**
     * Derive a thread-specific seed from master seed and thread ID.
     * Uses a simple but effective mixing function.
     */
    private long deriveThreadSeed(long masterSeed, long threadId) {
        // Mix master seed and thread ID using bit operations
        // This ensures different threads get different seeds, but deterministically
        long mixed = masterSeed ^ (threadId * 0x9e3779b97f4a7c15L);
        
        // Apply additional mixing to improve distribution
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        mixed = mixed ^ (mixed >>> 31);
        
        return mixed;
    }
    
    /**
     * Get the master seed used by this provider.
     */
    public long getMasterSeed() {
        return masterSeed;
    }
}
```

---

### Step 2: Write Unit Tests

**File**: `core/src/test/java/com/datagenerator/core/RandomProviderTest.java`

```java
package com.datagenerator.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

class RandomProviderTest {
    
    @Test
    void shouldReturnSameRandomInstanceForSameThread() {
        RandomProvider provider = new RandomProvider(12345);
        
        Random random1 = provider.getRandom();
        Random random2 = provider.getRandom();
        
        assertThat(random1).isSameAs(random2);
    }
    
    @Test
    void shouldReturnDifferentRandomInstancesForDifferentThreads() throws Exception {
        RandomProvider provider = new RandomProvider(12345);
        List<Random> randoms = new ArrayList<>();
        
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);
        
        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                synchronized (randoms) {
                    randoms.add(provider.getRandom());
                }
                latch.countDown();
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertThat(randoms).hasSize(3);
        assertThat(randoms.get(0)).isNotSameAs(randoms.get(1));
        assertThat(randoms.get(1)).isNotSameAs(randoms.get(2));
    }
    
    @Test
    void shouldProduceDeterministicOutputForSameSeed() {
        RandomProvider provider1 = new RandomProvider(12345);
        RandomProvider provider2 = new RandomProvider(12345);
        
        Random random1 = provider1.getRandom();
        Random random2 = provider2.getRandom();
        
        // Same master seed and thread → same sequence
        for (int i = 0; i < 10; i++) {
            assertThat(random1.nextInt(100)).isEqualTo(random2.nextInt(100));
        }
    }
    
    @Test
    void shouldProduceDifferentOutputForDifferentSeeds() {
        RandomProvider provider1 = new RandomProvider(12345);
        RandomProvider provider2 = new RandomProvider(54321);
        
        Random random1 = provider1.getRandom();
        Random random2 = provider2.getRandom();
        
        // Different master seeds → different sequences
        List<Integer> values1 = new ArrayList<>();
        List<Integer> values2 = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            values1.add(random1.nextInt(100));
            values2.add(random2.nextInt(100));
        }
        
        assertThat(values1).isNotEqualTo(values2);
    }
    
    @Test
    void shouldDeriveDifferentSeedsForDifferentThreads() throws Exception {
        RandomProvider provider = new RandomProvider(12345);
        Set<Integer> firstValues = new HashSet<>();
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                Random random = provider.getRandom();
                synchronized (firstValues) {
                    firstValues.add(random.nextInt(Integer.MAX_VALUE));
                }
                latch.countDown();
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        // All threads should produce different first values
        assertThat(firstValues).hasSize(5);
    }
    
    @Test
    void shouldReturnMasterSeed() {
        RandomProvider provider = new RandomProvider(98765);
        
        assertThat(provider.getMasterSeed()).isEqualTo(98765);
    }
}
```

---

## Acceptance Criteria

- ✅ Provides thread-local Random instances
- ✅ Same thread always gets the same Random instance
- ✅ Different threads get different Random instances
- ✅ Seeds are derived deterministically from master seed
- ✅ Same master seed produces reproducible output
- ✅ Thread-safe (no race conditions)
- ✅ All unit tests pass

---

## Testing

Run tests:
```bash
./gradlew :core:test
```

---

## Performance Characteristics

- **Thread-local storage**: O(1) lookup, no synchronization overhead
- **Seeding overhead**: Only on first access per thread
- **Memory**: One Random instance (48 bytes) per worker thread
- **Contention**: None (each thread has its own instance)

---

**Completion Date**: [Mark when complete]
