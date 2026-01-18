package com.datagenerator.generators.primitive;

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorException;
import java.util.Random;

/**
 * Generates random boolean values.
 *
 * <p><b>Algorithm:</b> Uses Random.nextBoolean() for uniform distribution (50% true, 50% false).
 *
 * <p><b>Future Enhancement:</b> Support weighted distributions (e.g., 70% true, 30% false).
 */
public class BooleanGenerator implements DataGenerator {

  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof PrimitiveType primitiveType)) {
      throw new GeneratorException(
          "BooleanGenerator requires PrimitiveType, got: " + dataType.getClass().getSimpleName());
    }
    if (primitiveType.getKind() != PrimitiveType.Kind.BOOLEAN) {
      throw new GeneratorException(
          "BooleanGenerator requires BOOLEAN type, got: " + primitiveType.getKind());
    }

    return random.nextBoolean();
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof PrimitiveType primitiveType
        && primitiveType.getKind() == PrimitiveType.Kind.BOOLEAN;
  }
}
