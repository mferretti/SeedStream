package com.datagenerator.generators.primitive;

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorException;
import java.util.List;
import java.util.Random;

/**
 * Generates random enum values from a predefined set of allowed values.
 *
 * <p><b>Algorithm:</b> Random selection from the enum value list with uniform distribution.
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * enum[ACTIVE,INACTIVE,PENDING] → randomly returns "ACTIVE", "INACTIVE", or "PENDING"
 * </pre>
 *
 * <p><b>Future Enhancement:</b> Support weighted distributions (some values more likely than
 * others).
 */
public class EnumGenerator implements DataGenerator {

  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof EnumType enumType)) {
      throw new GeneratorException(
          "EnumGenerator requires EnumType, got: " + dataType.getClass().getSimpleName());
    }

    List<String> values = enumType.getValues();
    if (values.isEmpty()) {
      throw new GeneratorException("EnumType has no values to generate from");
    }

    // Randomly select one value from the list
    int index = random.nextInt(values.size());
    return values.get(index);
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof EnumType;
  }
}
