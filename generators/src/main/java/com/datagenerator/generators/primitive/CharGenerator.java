package com.datagenerator.generators.primitive;

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorException;
import java.util.Random;

/**
 * Generates random strings (char[min..max]) with length constraints.
 *
 * <p><b>Performance:</b> Uses StringBuilder and random character selection from a predefined
 * alphabet for fast generation.
 *
 * <p><b>Alphabet:</b> Lowercase + uppercase letters (a-z, A-Z) for realistic-looking strings.
 * Future: Support custom alphabets or locale-specific characters.
 */
public class CharGenerator implements DataGenerator {
  // Alphabet: a-z, A-Z (52 characters)
  private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof PrimitiveType primitiveType)) {
      throw new GeneratorException(
          "CharGenerator requires PrimitiveType, got: " + dataType.getClass().getSimpleName());
    }
    if (primitiveType.getKind() != PrimitiveType.Kind.CHAR) {
      throw new GeneratorException(
          "CharGenerator requires CHAR type, got: " + primitiveType.getKind());
    }

    // Parse min/max length
    int minLength = parseLength(primitiveType.getMinValue(), "minValue");
    int maxLength = parseLength(primitiveType.getMaxValue(), "maxValue");

    if (minLength > maxLength) {
      throw new GeneratorException(
          "Invalid char range: minValue (" + minLength + ") > maxValue (" + maxLength + ")");
    }

    // Generate random length in range [minLength, maxLength]
    int length = minLength + random.nextInt(maxLength - minLength + 1);

    // Generate string with random characters from alphabet
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      int index = random.nextInt(ALPHABET.length());
      sb.append(ALPHABET.charAt(index));
    }

    return sb.toString();
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof PrimitiveType primitiveType
        && primitiveType.getKind() == PrimitiveType.Kind.CHAR;
  }

  private int parseLength(String value, String fieldName) {
    if (value == null) {
      throw new GeneratorException("Missing required field: " + fieldName + " for char type");
    }
    try {
      int length = Integer.parseInt(value);
      if (length < 0) {
        throw new GeneratorException(
            "Invalid " + fieldName + " for char type: " + length + " (must be >= 0)");
      }
      return length;
    } catch (NumberFormatException e) {
      throw new GeneratorException(
          "Invalid " + fieldName + " for char type: " + value + " (expected integer)", e);
    }
  }
}
