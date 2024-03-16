package com.igormaznitsa.cyberneuro.core;

import static java.lang.String.format;

import java.lang.reflect.Array;
import java.util.Objects;

public final class CyberNeuron implements CyberNetEntity, HasOutput, HasLock {

  private static final int THRESHOLD_NO = Byte.MAX_VALUE / 5;
  private static final int THRESHOLD_YES = Byte.MAX_VALUE - THRESHOLD_NO;
  private static final int THRESHOLD_MIDDLE = Byte.MAX_VALUE / 2;
  private final int inputSize;
  private final int rowLength;
  private final long uid;
  private final byte[] table;

  private boolean locked;

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
    return new CyberNeuron(UID_GENERATOR.incrementAndGet(), inputSize, maxValue);
  }

  private static void fillByPseudoRnd(final byte[] array) {
    int seed = array.length;
    for (int i = 0; i < array.length; i++) {
      seed = (seed * 73129 + 95121) % 100000;
      array[i] = (byte) seed;
    }
  }

  @Override
  public void setLock(boolean flag) {
    this.locked = flag;
  }

  public boolean isLocked() {
    return this.locked;
  }

  @Override
  public CyberNetEntity makeCopy() {
    final CyberNeuron result =
        new CyberNeuron(UID_GENERATOR.incrementAndGet(), this.inputSize, this.rowLength - 1);
    result.locked = this.locked;
    System.arraycopy(this.table, 0, result.table, 0, this.table.length);
    return result;
  }

  @Override
  public long getUid() {
    return this.uid;
  }

  @Override
  public boolean isInputIndexValid(final int index) {
    return index >= 0 && index < this.inputSize;
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
    return object instanceof CyberNeuron cyberNeuron
        && (this.uid == cyberNeuron.uid);
  }

  public int getRowLength() {
    return this.rowLength;
  }

  int getTableValue(final int index) {
    return this.table[index];
  }

  void setTableValue(final int index, final int value) {
    this.assertNonLocked();
    this.table[index] = (byte) value;
  }

  @Override
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
    this.assertNonLocked();
    this.assertArraySize(values);
    for (int i = 0; i < values.length; i++) {
      this.setTableValue(i, values[i]);
    }
  }

  public void teach(final int[] inputVector, final LearnStrategy learnStrategy,
                    final ConfidenceDegree expectedConfidence) {
    this.assertNonLocked();
    if (this.inputSize != inputVector.length) {
      throw new IllegalArgumentException(
          format("Wrong input size: %d != %d", this.inputSize, inputVector.length));
    }
    if (this.check(inputVector) == expectedConfidence) {
      return;
    }

    final int expectedMin;
    final int expectedMax;
    switch (expectedConfidence) {
      case YES: {
        expectedMin = THRESHOLD_YES + 1;
        expectedMax = Byte.MAX_VALUE;
      }
      break;
      case MAY_BE_YES: {
        expectedMin = THRESHOLD_MIDDLE + 1;
        expectedMax = THRESHOLD_YES - 1;
      }
      break;
      case NO: {
        expectedMin = Byte.MIN_VALUE;
        expectedMax = THRESHOLD_NO - 1;
      }
      break;
      case MAY_BE_NO: {
        expectedMin = THRESHOLD_NO;
        expectedMax = THRESHOLD_MIDDLE - 1;
      }
      break;
      default:
        throw new IllegalArgumentException("Unsupported confidence: " + expectedConfidence);
    }

    final int current = this.calc(inputVector);
    final int diff;
    if (current < expectedMin) {
      diff = expectedMin - current;
    } else if (current > expectedMax) {
      diff = expectedMax - current;
    } else {
      throw new IllegalStateException("Unexpected state");
    }

    learnStrategy.accept(this, inputVector, diff);
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
}
