package com.igormaznitsa.cyberneuro.core;

import java.lang.reflect.Array;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class CyberNeuron {

  private static final AtomicLong GENERATOR_ID = new AtomicLong();

  private static final int THRESHOLD_NO = Byte.MAX_VALUE / 5;
  private static final int THRESHOLD_YES = Byte.MAX_VALUE - THRESHOLD_NO;
  private static final int THRESHOLD_MIDDLE = Byte.MAX_VALUE / 2;
  private final int inputSize;
  private final int rowLength;
  private final long uid;
  private final byte[] table;

  public CyberNeuron(
      final long uid,
      final int inputSize,
      final int maxInputValue
  ) {
    this.uid = uid;
    this.inputSize = inputSize;
    this.rowLength = maxInputValue + 1;
    this.table = new byte[inputSize * this.rowLength];
    fillByPseudoRnd(this.table);
  }

  public static CyberNeuron of(
      final int inputSize,
      final int maxValue
  ) {
    if (inputSize <= 0) {
      throw new IllegalArgumentException("Number of inputs must be positive one");
    }
    if (maxValue < 0) {
      throw new IllegalArgumentException("Max value must not be negative one");
    }
    return new CyberNeuron(GENERATOR_ID.incrementAndGet(), inputSize, maxValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.uid);
  }

  @Override
  public boolean equals(final Object object) {
    if (object == null) {
      return false;
    }
    if (object == this) {
      return true;
    }
    return object instanceof CyberNeuron
        && (this.uid == ((CyberNeuron) object).uid);
  }

  public int getRowLength() {
    return this.rowLength;
  }

  protected int getTableValue(final int index) {
    return this.table[index];
  }

  protected void setTableValue(final int index, final int value) {
    this.table[index] = (byte) value;
  }

  public int getInputSize() {
    return this.inputSize;
  }

  private void assertArraySize(final Object array) {
    final int expected = this.rowLength * this.inputSize;
    final int provided = Array.getLength(array);
    if (provided != expected) {
      throw new IllegalArgumentException(
          "Expected size of array is " + expected + " but provided " + provided);
    }
  }

  public void fill(final byte[] values) {
    assertArraySize(values);
    for (int i = 0; i < values.length; i++) {
      this.setTableValue(i, values[i]);
    }
  }

  public void teach(final int[] inputs, final LearnStrategy learnStrategy,
                    final ConfidenceDegree confidenceDegree) {
    if (this.inputSize != inputs.length) {
      throw new IllegalArgumentException(
          "Wrong input size: " + this.inputSize + " != " + inputs.length);
    }
    final int output = this.calc(inputs);
    final int modifier;
    switch (confidenceDegree) {
      case YES: {
        if (output > THRESHOLD_YES) {
          modifier = 0;
        } else {
          modifier = Math.max(1, (THRESHOLD_YES + 1) - output);
        }
      }
      break;
      case MAY_BE_YES: {
        if (output > THRESHOLD_MIDDLE) {
          modifier = 0;
        } else {
          modifier = Math.max(1, THRESHOLD_MIDDLE - output);
        }
      }
      break;
      case NO: {
        if (output < THRESHOLD_NO) {
          modifier = 0;
        } else {
          modifier = Math.min(-1, (THRESHOLD_NO - 1) - output);
        }
      }
      break;
      case MAY_BE_NO: {
        if (output < THRESHOLD_MIDDLE && output > THRESHOLD_NO) {
          modifier = 0;
        } else {
          if (output < THRESHOLD_NO) {
            modifier = Math.max(1, (THRESHOLD_NO + 1) - output);
          } else {
            if (output > THRESHOLD_MIDDLE) {
              modifier = Math.min(-1, (THRESHOLD_MIDDLE - 1) - output);
            } else {
              modifier = 0;
            }
          }
        }
      }
      break;
      default:
        throw new IllegalArgumentException("Unsupported confidence: " + confidenceDegree);
    }
    if (modifier != 0) {
      learnStrategy.accept(this, inputs, modifier);
    }
  }

  public ConfidenceDegree check(final int[] inputs) {
    return this.check(0, inputs);
  }

  public ConfidenceDegree check(final int offset, final int[] inputs) {
    final int calculated = calc(offset, inputs);
    if (calculated > THRESHOLD_YES) {
      return ConfidenceDegree.YES;
    }
    if (calculated > THRESHOLD_MIDDLE) {
      return ConfidenceDegree.MAY_BE_YES;
    }
    if (calculated > THRESHOLD_NO) {
      return ConfidenceDegree.MAY_BE_NO;
    }
    return ConfidenceDegree.NO;
  }

  public int calc(final int[] inputs) {
    return this.calc(0, inputs);
  }

  public int calc(final int offset, final int[] inputs) {
    if (inputs.length - offset < this.inputSize) {
      throw new IllegalArgumentException("Unexpected inputs size: " + (inputs.length - offset));
    }
    int acc = 0;
    int offsetRow = 0;
    for (int i = 0; i < this.inputSize; i++) {
      acc += this.getTableValue(offsetRow + inputs[i + offset]);
      offsetRow += this.rowLength;
    }
    return acc;
  }

  public String asText() {
    final StringBuilder buffer = new StringBuilder();
    buffer.append("CyberNeuron: [uid=").append(this.uid).append(", ");
    int offset = 0;
    for (int i = 0; i < this.inputSize; i++) {
      if (i > 0) {
        buffer.append(", ");
      }
      buffer.append('(');
      for (int j = 0; j < this.rowLength; j++) {
        if (j != 0) {
          buffer.append(", ");
        }
        buffer.append(this.getTableValue(offset + j));
      }
      buffer.append(')');
      offset += this.rowLength;
    }
    buffer.append(']');
    return buffer.toString();
  }

  private static void fillByPseudoRnd(final byte[] array) {
    int seed = array.length;
    for (int i = 0; i < array.length; i++) {
      seed = (seed * 73129 + 95121) % 100000;
      array[i] = (byte) seed;
    }
  }
}
