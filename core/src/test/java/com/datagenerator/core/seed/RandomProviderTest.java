package com.datagenerator.core.seed;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RandomProviderTest {

  @Test
  void shouldProvideDifferentRandomInstancesPerThread() throws Exception {
    RandomProvider provider = new RandomProvider(12345L);
    Set<Random> instances = ConcurrentHashMap.newKeySet();

    ExecutorService executor = Executors.newFixedThreadPool(5);
    List<Future<Random>> futures = new ArrayList<>();

    for (int i = 0; i < 5; i++) {
      futures.add(executor.submit(provider::getRandom));
    }

    for (Future<Random> future : futures) {
      instances.add(future.get());
    }

    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    assertThat(instances).hasSize(5); // Each thread got its own instance
  }

  @Test
  void shouldProduceSameSequenceWithSameSeed() {
    RandomProvider provider1 = new RandomProvider(42L);
    RandomProvider provider2 = new RandomProvider(42L);

    List<Integer> sequence1 = new ArrayList<>();
    List<Integer> sequence2 = new ArrayList<>();

    for (int i = 0; i < 100; i++) {
      sequence1.add(provider1.getRandom().nextInt());
      sequence2.add(provider2.getRandom().nextInt());
    }

    assertThat(sequence1).isEqualTo(sequence2);
  }

  @Test
  void shouldProduceDifferentSequencesWithDifferentSeeds() {
    RandomProvider provider1 = new RandomProvider(42L);
    RandomProvider provider2 = new RandomProvider(99L);

    List<Integer> sequence1 = new ArrayList<>();
    List<Integer> sequence2 = new ArrayList<>();

    for (int i = 0; i < 100; i++) {
      sequence1.add(provider1.getRandom().nextInt());
      sequence2.add(provider2.getRandom().nextInt());
    }

    assertThat(sequence1).isNotEqualTo(sequence2);
  }

  @Test
  void shouldDeriveDistinctSeedsForDifferentThreads() throws Exception {
    RandomProvider provider = new RandomProvider(12345L);
    Map<Integer, List<Integer>> workerSequences = new ConcurrentHashMap<>();
    AtomicInteger workerCounter = new AtomicInteger(0);

    ExecutorService executor = Executors.newFixedThreadPool(3);
    CountDownLatch latch = new CountDownLatch(3);

    for (int i = 0; i < 3; i++) {
      executor.submit(
          () -> {
            int workerId = workerCounter.getAndIncrement();
            List<Integer> sequence = new ArrayList<>();
            Random random = provider.getRandom();
            for (int j = 0; j < 50; j++) {
              sequence.add(random.nextInt());
            }
            workerSequences.put(workerId, sequence);
            latch.countDown();
          });
    }

    latch.await(5, TimeUnit.SECONDS);
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    assertThat(workerSequences).hasSize(3);
    List<List<Integer>> sequences = new ArrayList<>(workerSequences.values());
    // All sequences should be different (derived from different worker IDs)
    assertThat(sequences.get(0)).isNotEqualTo(sequences.get(1));
    assertThat(sequences.get(1)).isNotEqualTo(sequences.get(2));
    assertThat(sequences.get(0)).isNotEqualTo(sequences.get(2));
  }

  @Test
  void shouldProduceSameSequencesAcrossRunsWithSameWorkerOrder() throws Exception {
    // Run 1
    RandomProvider provider1 = new RandomProvider(999L);
    List<List<Integer>> run1Sequences = generateSequencesInOrder(provider1, 3);

    // Run 2 with same seed
    RandomProvider provider2 = new RandomProvider(999L);
    List<List<Integer>> run2Sequences = generateSequencesInOrder(provider2, 3);

    // Same worker order → same sequences (deterministic)
    assertThat(run1Sequences.get(0)).isEqualTo(run2Sequences.get(0));
    assertThat(run1Sequences.get(1)).isEqualTo(run2Sequences.get(1));
    assertThat(run1Sequences.get(2)).isEqualTo(run2Sequences.get(2));
  }

  private List<List<Integer>> generateSequencesInOrder(RandomProvider provider, int workerCount) {
    List<List<Integer>> sequences = new ArrayList<>();
    for (int i = 0; i < workerCount; i++) {
      List<Integer> sequence = new ArrayList<>();
      Random random = provider.getRandom();
      for (int j = 0; j < 50; j++) {
        sequence.add(random.nextInt());
      }
      sequences.add(sequence);
    }
    return sequences;
  }

  @Test
  void shouldProduceReproducibleSequenceForSameWorker() throws Exception {
    RandomProvider provider1 = new RandomProvider(999L);
    RandomProvider provider2 = new RandomProvider(999L);

    // Same master seed should produce same worker sequences
    List<Integer> sequence1 = new ArrayList<>();
    List<Integer> sequence2 = new ArrayList<>();

    for (int i = 0; i < 50; i++) {
      sequence1.add(provider1.getRandom().nextInt());
      sequence2.add(provider2.getRandom().nextInt());
    }

    // Both providers with same seed should produce identical sequences for worker 0
    assertThat(sequence1).isEqualTo(sequence2);
  }

  @Test
  void shouldReturnMasterSeed() {
    RandomProvider provider = new RandomProvider(777L);

    assertThat(provider.getMasterSeed()).isEqualTo(777L);
  }

  @Test
  void shouldReuseRandomInstanceForSameThread() {
    RandomProvider provider = new RandomProvider(12345L);

    Random first = provider.getRandom();
    Random second = provider.getRandom();

    assertThat(first).isSameAs(second); // Same instance for same thread
  }

  @Test
  void shouldHandleNegativeSeed() {
    RandomProvider provider = new RandomProvider(-12345L);

    assertThat(provider.getMasterSeed()).isEqualTo(-12345L);
    assertThatCode(() -> provider.getRandom().nextInt()).doesNotThrowAnyException();
  }

  @Test
  void shouldHandleZeroSeed() {
    RandomProvider provider = new RandomProvider(0L);

    assertThat(provider.getMasterSeed()).isEqualTo(0L);
    assertThatCode(() -> provider.getRandom().nextInt()).doesNotThrowAnyException();
  }

  @Test
  void shouldCleanupThreadLocal() {
    RandomProvider provider = new RandomProvider(42L);
    provider.getRandom(); // Initialize thread-local

    assertThatCode(provider::cleanup).doesNotThrowAnyException();

    // After cleanup, accessing again should create a new instance
    Random afterCleanup = provider.getRandom();
    assertThat(afterCleanup).isNotNull();
  }
}
