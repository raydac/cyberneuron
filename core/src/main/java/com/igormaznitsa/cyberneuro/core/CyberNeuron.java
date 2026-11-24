package com.igormaznitsa.cyberneuro.core;

import static java.lang.String.format;

import java.lang.reflect.Array;
import java.util.List;

/**
 * Substitution-table neuron.
 *
 * <p>Each input owns a row of signed bytes. A symbol is an index into that row,
 * and {@link #calc(int[])} is the sum of the addressed cells. Nothing multiplies
 * the symbol by a weight. {@link #check(int, int[])} places the sum on the
 * {@link ConfidenceDegree} scale. {@link #teach(int[], LearnStrategy, ConfidenceDegree)}
 * adds or subtracts one in addressed cells until the sum enters the requested band.
 *
 * <p>Rows are independent, so one neuron cannot separate a non-additive function
 * of its inputs. Pack interacting bits into one symbol, or assemble a
 * {@link CyberNetwork} whose hidden neurons start from different tables.
 * The raw sum is not itself a legal index of an arbitrary next row; publish
 * {@link #signal(int[])} when the next alphabet is the four confidence bands.
 *
 * <p>Not thread-safe. {@link #setLock(boolean)} makes later writes throw.
 */
public class CyberNeuron implements HasLock {

  private static final int THRESHOLD_NO = Byte.MAX_VALUE / 5;
  private static final int THRESHOLD_YES = Byte.MAX_VALUE - THRESHOLD_NO;
  private static final int THRESHOLD_MIDDLE = Byte.MAX_VALUE / 2;
  private final int inputSize;
  private final int rowLength;
  private final byte[] table;

  private boolean locked;

  /**
   * Creates a neuron and fills the table from a fixed pseudo-random sequence.
   * Range checks live in {@link #of(int, int)}; this constructor trusts the numbers.
   *
   * @param inputSize     number of substitution rows
   * @param maxInputValue largest legal symbol; each row has {@code maxInputValue + 1} cells
   */
  public CyberNeuron(final int inputSize, final int maxInputValue) {
    this.inputSize = inputSize;
    this.rowLength = maxInputValue + 1;
    this.table = new byte[inputSize * this.rowLength];
    fillByPseudoRnd(this.table);
  }

  /**
   * Creates a neuron.
   *
   * @param inputSize number of substitution rows; must be positive
   * @param maxValue largest legal symbol on every row; zero yields a one-cell row
   * @return neuron whose table is a fixed pseudo-random fill
   * @throws IllegalArgumentException when {@code inputSize} is not positive
   *     or {@code maxValue} is negative
   */
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
    return new CyberNeuron(inputSize, maxValue);
  }

  private static void fillByPseudoRnd(final byte[] array) {
    int seed = array.length;
    for (int i = 0; i < array.length; i++) {
      seed = (seed * 73129 + 95121) % 100000;
      array[i] = (byte) seed;
    }
  }

  private static int scaledStep(final int diff, final int learningDivider) {
    final int step = diff / learningDivider;
    if (step != 0) {
      return step;
    }
    return diff > 0 ? 1 : -1;
  }

  public boolean isLocked() {
    return this.locked;
  }

  /**
   * Freezes or releases table writes.
   *
   * <p>A locked neuron rejects {@link #fill(byte[])} and
   * {@link #teach(int[], LearnStrategy, ConfidenceDegree)}.
   *
   * @param flag {@code true} to reject later writes, {@code false} to allow them
   */
  @Override
  public void setLock(boolean flag) {
    this.locked = flag;
  }

  int getTableValue(final int index) {
    return this.table[index];
  }

  void setTableValue(final int index, final int value) {
    this.assertNonLocked();
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

  /**
   * Cells in one substitution row, one past the largest legal symbol.
   *
   * @return row length fixed at construction
   */
  public int getRowLength() {
    return this.rowLength;
  }

  /**
   * Replaces every table cell.
   *
   * @param values row-major bytes, length {@code inputSize * rowLength}
   * @throws IllegalArgumentException when {@code values} has a different length
   * @throws IllegalStateException when the neuron is locked
   */
  public void fill(final byte[] values) {
    this.assertNonLocked();
    this.assertArraySize(values);
    for (int i = 0; i < values.length; i++) {
      this.setTableValue(i, values[i]);
    }
  }

  /**
   * Moves the sum for {@code inputVector} into the band of {@code expectedConfidence}.
   * A sample that already lands in that band is left untouched. The whole gap is
   * applied in this call; use the four-argument method to scale it.
   *
   * @param inputVector one symbol per row, each inside {@code 0..rowLength-1}
   * @param learnStrategy how the correction is spread across the addressed cells
   * @param expectedConfidence band the sum should enter
   * @throws IllegalArgumentException when the vector length or a symbol is illegal
   * @throws IllegalStateException when the neuron is locked, or the sum is already
   *     inside the numeric band while the confidence label differs
   */
  public void teach(final int[] inputVector, final LearnStrategy learnStrategy,
                    final ConfidenceDegree expectedConfidence) {
    this.teach(inputVector, learnStrategy, expectedConfidence, 1);
  }

  /**
   * Signed change that would put {@code sum} into {@code expected}.
   * Zero when the sum is already in that band, or when it sits in the narrow
   * window where the band label and the numeric edges disagree.
   *
   * @param sum      neuron sum
   * @param expected band the sum should enter
   * @return positive to raise the sum, negative to lower it
   */
  static int signedGap(final int sum, final ConfidenceDegree expected) {
    final int lower = lowerEdge(expected);
    final int upper = upperEdge(expected);
    if (sum < lower) {
      return lower - sum;
    }
    if (sum > upper) {
      return upper - sum;
    }
    return 0;
  }

  private static int lowerEdge(final ConfidenceDegree expected) {
    switch (expected) {
      case YES:
        return THRESHOLD_YES + 1;
      case MAY_BE_YES:
        return THRESHOLD_MIDDLE + 1;
      case NO:
        return Byte.MIN_VALUE;
      case MAY_BE_NO:
        return THRESHOLD_NO;
      default:
        throw new IllegalArgumentException("Unsupported confidence: " + expected);
    }
  }

  private static int upperEdge(final ConfidenceDegree expected) {
    switch (expected) {
      case YES:
        return Byte.MAX_VALUE;
      case MAY_BE_YES:
        return THRESHOLD_YES - 1;
      case NO:
        return THRESHOLD_NO - 1;
      case MAY_BE_NO:
        return THRESHOLD_MIDDLE - 1;
      default:
        throw new IllegalArgumentException("Unsupported confidence: " + expected);
    }
  }

  static ConfidenceDegree bandOf(final int sum) {
    if (sum > THRESHOLD_YES) {
      return ConfidenceDegree.YES;
    }
    if (sum > THRESHOLD_MIDDLE) {
      return ConfidenceDegree.MAY_BE_YES;
    }
    if (sum > THRESHOLD_NO) {
      return ConfidenceDegree.MAY_BE_NO;
    }
    return ConfidenceDegree.NO;
  }

  /**
   * Same band correction as {@link #teach(int[], LearnStrategy, ConfidenceDegree)},
   * with the paper's learning divider. The step count is the gap divided by
   * {@code learningDivider}, and at least one step while the sum is still outside
   * the band. Divider {@code 4} matches the rate in the paper's experiments.
   *
   * @param inputVector        one symbol per row, each inside {@code 0..rowLength-1}
   * @param learnStrategy      how the correction is spread across the addressed cells
   * @param expectedConfidence band the sum should enter
   * @param learningDivider    positive divisor of the raw gap; {@code 1} spends the whole gap
   * @throws IllegalArgumentException when {@code learningDivider} is not positive,
   *                                  or the vector length or a symbol is illegal
   * @throws IllegalStateException    when the neuron is locked, or the sum is already
   *                                  inside the numeric band while the confidence label differs
   */
  public void teach(final int[] inputVector, final LearnStrategy learnStrategy,
                    final ConfidenceDegree expectedConfidence, final int learningDivider) {
    this.assertNonLocked();
    if (learningDivider < 1) {
      throw new IllegalArgumentException("Learning divider must be positive");
    }
    if (this.inputSize != inputVector.length) {
      throw new IllegalArgumentException(
          format("Wrong input size: %d != %d", this.inputSize, inputVector.length));
    }
    final int current = this.calc(inputVector);
    if (bandOf(current) == expectedConfidence) {
      return;
    }
    final int diff = signedGap(current, expectedConfidence);
    if (diff == 0) {
      throw new IllegalStateException("Unexpected state");
    }

    learnStrategy.accept(this, inputVector, scaledStep(diff, learningDivider));
  }

  /**
   * Applies {@code steps} unit edits to the cells addressed by {@code inputs}.
   * {@code learningDivider} keeps the same meaning as in {@link #teach}.
   *
   * @param inputs          one symbol per row
   * @param learnStrategy   how the steps are spread across the addressed cells
   * @param steps           signed step count; zero does nothing
   * @param learningDivider positive divisor of {@code steps}
   */
  void nudge(
      final int[] inputs,
      final LearnStrategy learnStrategy,
      final int steps,
      final int learningDivider) {
    this.assertNonLocked();
    if (learningDivider < 1) {
      throw new IllegalArgumentException("Learning divider must be positive");
    }
    if (steps == 0) {
      return;
    }
    learnStrategy.accept(this, inputs, scaledStep(steps, learningDivider));
  }

  /**
   * Symbol safe to feed a downstream neuron whose alphabet is the confidence scale.
   * The value is {@link ConfidenceDegree#ordinal()} and therefore in {@code 0..3}.
   *
   * @param inputs one symbol per row
   * @return ordinal of {@link #check(int, int[])} at offset zero
   * @throws IllegalArgumentException when {@code inputs} cannot be addressed
   */
  public int signal(final int[] inputs) {
    return this.check(0, inputs).ordinal();
  }

  /**
   * Whether the sum sits in the recognized half of the scale.
   *
   * @param inputs one symbol per row
   * @return {@code true} for {@link ConfidenceDegree#MAY_BE_YES} or {@link ConfidenceDegree#YES}
   * @throws IllegalArgumentException when {@code inputs} cannot be addressed
   */
  public boolean matches(final int[] inputs) {
    final ConfidenceDegree confidence = this.check(0, inputs);
    return confidence == ConfidenceDegree.YES || confidence == ConfidenceDegree.MAY_BE_YES;
  }

  /**
   * Band of {@link #calc(int[])} for this sample.
   *
   * @param inputs one symbol per row
   * @return list of one band, the same value {@link #check(int, int[])} returns at offset zero
   * @throws IllegalArgumentException when {@code inputs} cannot be addressed
   */
  public List<ConfidenceDegree> activateAsConfidence(final int[] inputs) {
    return List.of(this.check(0, inputs));
  }

  /**
   * Band of the sum of rows read from {@code inputs} starting at {@code offset}.
   *
   * @param offset index of the symbol for row 0
   * @param inputs symbols; the tail from {@code offset} must cover every row
   * @return recognition band of that sum
   * @throws IllegalArgumentException when the tail is short or a symbol is outside its row
   */
  public ConfidenceDegree check(final int offset, final int[] inputs) {
    return bandOf(this.calc(offset, inputs));
  }

  /**
   * Sum of the cells addressed by {@code inputs}, starting at row 0.
   *
   * @param inputs one symbol per row, each inside {@code 0..rowLength-1}
   * @return signed sum; the magnitude is not clipped to a row index
   * @throws IllegalArgumentException when the vector length or a symbol is illegal
   */
  public int calc(final int[] inputs) {
    return this.calc(0, inputs);
  }

  /**
   * Sum of the cells addressed by the tail of {@code inputs} that starts at {@code offset}.
   *
   * @param offset index of the symbol for row 0
   * @param inputs symbols; the tail from {@code offset} must cover every row
   * @return signed sum of those cells
   * @throws IllegalArgumentException when the tail is short or a symbol is outside its row
   */
  public int calc(final int offset, final int[] inputs) {
    if (inputs.length - offset < this.inputSize) {
      throw new IllegalArgumentException("Unexpected inputs size: " + (inputs.length - offset));
    }
    int acc = 0;
    int offsetRow = 0;
    for (int i = 0; i < this.inputSize; i++) {
      final int value = inputs[i + offset];
      if (value < 0 || value >= this.rowLength) {
        throw new IllegalArgumentException(format(
            "Input %d has value %d outside 0..%d", i, value, this.rowLength - 1));
      }
      acc += this.getTableValue(offsetRow + value);
      offsetRow += this.rowLength;
    }
    return acc;
  }

  /**
   * Row-major text of the table.
   *
   * @return line starting with {@code CyberNeuron:}
   */
  public String asText() {
    final StringBuilder buffer = new StringBuilder();
    buffer.append("CyberNeuron: [");
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
