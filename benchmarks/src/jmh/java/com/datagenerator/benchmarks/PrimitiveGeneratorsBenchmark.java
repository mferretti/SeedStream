package com.datagenerator.benchmarks;

import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.primitive.BooleanGenerator;
import com.datagenerator.generators.primitive.CharGenerator;
import com.datagenerator.generators.primitive.DateGenerator;
import com.datagenerator.generators.primitive.DecimalGenerator;
import com.datagenerator.generators.primitive.EnumGenerator;
import com.datagenerator.generators.primitive.IntegerGenerator;
import com.datagenerator.generators.primitive.TimestampGenerator;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks for primitive data generators. Validates NFR-1 requirement of 10M records/second for
 * in-memory primitive generation.
 *
 * <p><b>Target:</b> Each generator should achieve >10M ops/sec (100ns per operation)
 *
 * <p><b>Scenarios:</b> char, int, decimal, boolean, date, timestamp, enum
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class PrimitiveGeneratorsBenchmark {

  private Random random;
  private CharGenerator charGenerator;
  private IntegerGenerator intGenerator;
  private DecimalGenerator decimalGenerator;
  private BooleanGenerator booleanGenerator;
  private DateGenerator dateGenerator;
  private TimestampGenerator timestampGenerator;
  private EnumGenerator enumGenerator;

  private PrimitiveType charType;
  private PrimitiveType intType;
  private PrimitiveType decimalType;
  private PrimitiveType booleanType;
  private PrimitiveType dateType;
  private PrimitiveType timestampType;
  private PrimitiveType enumType;

  @Setup
  public void setup() {
    random = new Random(12345L);

    // Initialize generators
    charGenerator = new CharGenerator();
    intGenerator = new IntegerGenerator();
    decimalGenerator = new DecimalGenerator();
    booleanGenerator = new BooleanGenerator();
    dateGenerator = new DateGenerator();
    timestampGenerator = new TimestampGenerator();
    enumGenerator = new EnumGenerator();

    // Initialize types
    charType = new PrimitiveType(PrimitiveType.Kind.CHAR, "3", "15");
    intType = new PrimitiveType(PrimitiveType.Kind.INT, "1", "999");
    decimalType = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "100.0");
    booleanType = new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null);
    dateType = new PrimitiveType(PrimitiveType.Kind.DATE, "2020-01-01", "2025-12-31");
    timestampType = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, "now-30d", "now");
    enumType = new PrimitiveType(PrimitiveType.Kind.CHAR, "VALUE1,VALUE2,VALUE3", null);
  }

  @Benchmark
  public String benchmarkCharGenerator() {
    return (String) charGenerator.generate(random, charType);
  }

  @Benchmark
  public Integer benchmarkIntegerGenerator() {
    return (Integer) intGenerator.generate(random, intType);
  }

  @Benchmark
  public Object benchmarkDecimalGenerator() {
    return decimalGenerator.generate(random, decimalType);
  }

  @Benchmark
  public Boolean benchmarkBooleanGenerator() {
    return (Boolean) booleanGenerator.generate(random, booleanType);
  }

  @Benchmark
  public Object benchmarkDateGenerator() {
    return dateGenerator.generate(random, dateType);
  }

  @Benchmark
  public Object benchmarkTimestampGenerator() {
    return timestampGenerator.generate(random, timestampType);
  }
}
