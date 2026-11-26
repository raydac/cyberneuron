package com.igormaznitsa.cyberneuro.core;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Random;

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
public final class CyberNeuron implements HasLock {

  private static final int THRESHOLD_NO = Byte.MAX_VALUE / 5;
  private static final int THRESHOLD_YES = Byte.MAX_VALUE - THRESHOLD_NO;
  private static final int THRESHOLD_MIDDLE = Byte.MAX_VALUE / 2;

  private final int inputSize;
  private final int rowLength;
  private final byte[] table;

  private boolean locked;

  /**
   * Creates a neuron and fills the table from a fixed pseudo-random sequence.
   *
   * @param inputSize     number of substitution rows; must be positive
   * @param maxInputValue largest legal symbol; each row has {@code maxInputValue + 1} cells
   * @throws IllegalArgumentException when {@code inputSize} is not positive,
   *                                  {@code maxInputValue} is negative, or the table size overflows
   */
  public CyberNeuron(final int inputSize, final int maxInputValue) {
    this(TableSpec.ofDefaultRandom(inputSize, maxInputValue));
  }

  /**
   * Creates a neuron and fills the table from {@code random}.
   *
   * @param inputSize     number of substitution rows; must be positive
   * @param maxInputValue largest legal symbol; each row has {@code maxInputValue + 1} cells
   * @param random        source of initial cell bytes
   * @throws IllegalArgumentException when {@code inputSize} is not positive,
   *                                  {@code maxInputValue} is negative, or the table size overflows
   * @throws NullPointerException     when {@code random} is null
   */
  public CyberNeuron(final int inputSize, final int maxInputValue, final Random random) {
    this(TableSpec.ofRandom(inputSize, maxInputValue, random));
  }

  private CyberNeuron(final TableSpec spec) {
    this.inputSize = spec.inputSize;
    this.rowLength = spec.rowLength;
    this.table = spec.table;
  }

  /**
   * Creates a neuron with a deterministic pseudo-random table fill.
   *
   * @param inputSize number of substitution rows; must be positive
   * @param maxValue  largest legal symbol on every row; zero yields a one-cell row
   * @return neuron whose table is a fixed pseudo-random fill
   * @throws IllegalArgumentException when {@code inputSize} is not positive,
   *                                  {@code maxValue} is negative, or the table size overflows
   */
  public static CyberNeuron of(final int inputSize, final int maxValue) {
    return new CyberNeuron(inputSize, maxValue);
  }

  /**
   * Creates a neuron whose initial table bytes are drawn from {@code random}.
   *
   * @param inputSize number of substitution rows; must be positive
   * @param maxValue  largest legal symbol on every row; zero yields a one-cell row
   * @param random    source of initial cell bytes
   * @return neuron initialized from {@code random}
   * @throws IllegalArgumentException when {@code inputSize} is not positive,
   *                                  {@code maxValue} is negative, or the table size overflows
   * @throws NullPointerException     when {@code random} is null
   */
  public static CyberNeuron of(final int inputSize, final int maxValue, final Random random) {
    return new CyberNeuron(inputSize, maxValue, random);
  }

  private static int requirePositiveInputs(final int inputSize) {
    if (inputSize <= 0) {
      throw new IllegalArgumentException("Number of inputs must be positive one");
    }
    return inputSize;
  }

  private static int computeRowLength(final int maxInputValue) {
    if (maxInputValue < 0) {
      throw new IllegalArgumentException("Max value must not be negative one");
    }
    if (maxInputValue == Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Max value is too large");
    }
    return maxInputValue + 1;
  }

  private static int computeTableLength(final int inputSize, final int rowLength) {
    final long total = (long) inputSize * rowLength;
    if (total > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Neuron table size exceeds integer limit");
    }
    return (int) total;
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

  /**
   * Signed change that puts {@code sum} into {@code expected}, or zero when
   * {@code sum} is already inside that band.
   *
   * @param sum      neuron sum
   * @param expected band the sum should enter
   * @return positive to raise the sum, negative to lower it, zero when already in band
   */
  static int signedGap(final int sum, final ConfidenceDegree expected) {
    requireNonNull(expected, "expected must not be null");
    if (bandOf(sum) == expected) {
      return 0;
    }
    final int lower = lowerEdge(expected);
    final int upper = upperEdge(expected);
    if (sum < lower) {
      return saturateToInt((long) lower - sum);
    }
    if (sum > upper) {
      return saturateToInt((long) upper - sum);
    }
    return 0;
  }

  private static int saturateToInt(final long delta) {
    if (delta > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    if (delta < Integer.MIN_VALUE) {
      return Integer.MIN_VALUE;
    }
    return (int) delta;
  }

  private static int lowerEdge(final ConfidenceDegree expected) {
    switch (expected) {
      case YES:
        return THRESHOLD_YES + 1;
      case MAY_BE_YES:
        return THRESHOLD_MIDDLE + 1;
      case MAY_BE_NO:
        return THRESHOLD_NO + 1;
      case NO:
        return Integer.MIN_VALUE;
      default:
        throw new IllegalArgumentException("Unsupported confidence: " + expected);
    }
  }

  private static int upperEdge(final ConfidenceDegree expected) {
    switch (expected) {
      case YES:
        return Integer.MAX_VALUE;
      case MAY_BE_YES:
        return THRESHOLD_YES - 1;
      case MAY_BE_NO:
        return THRESHOLD_MIDDLE - 1;
      case NO:
        return THRESHOLD_NO - 1;
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

  @Override
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
  public void setLock(final boolean flag) {
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
   * @throws IllegalArgumentException when {@code values} is null or has a different length
   * @throws IllegalStateException    when the neuron is locked
   */
  public void fill(final byte[] values) {
    this.assertNonLocked();
    if (values == null) {
      throw new IllegalArgumentException("Values must not be null");
    }
    if (values.length != this.table.length) {
      throw new IllegalArgumentException(
          "Expected size of array is " + this.table.length + " but provided " + values.length);
    }
    System.arraycopy(values, 0, this.table, 0, values.length);
  }

  /**
   * Moves the sum for {@code inputVector} into the band of {@code expectedConfidence}.
   * A sample that already lands in that band is left untouched. The whole gap is
   * applied in this call; use the four-argument method to scale it.
   *
   * @param inputVector        one symbol per row, each inside {@code 0..rowLength-1}
   * @param learnStrategy      how the correction is spread across the addressed cells
   * @param expectedConfidence band the sum should enter
   * @throws IllegalArgumentException when the vector length or a symbol is illegal
   * @throws IllegalStateException    when the neuron is locked
   * @throws NullPointerException     when {@code learnStrategy}
   *                                  or {@code expectedConfidence} is null
   */
  public void teach(
      final int[] inputVector,
      final LearnStrategy learnStrategy,
      final ConfidenceDegree expectedConfidence) {
    this.teach(inputVector, learnStrategy, expectedConfidence, 1);
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
   * @throws IllegalStateException    when the neuron is locked
   * @throws NullPointerException     when {@code learnStrategy}
   *                                  or {@code expectedConfidence} is null
   */
  public void teach(
      final int[] inputVector,
      final LearnStrategy learnStrategy,
      final ConfidenceDegree expectedConfidence,
      final int learningDivider) {
    this.assertNonLocked();
    requireNonNull(learnStrategy, "learnStrategy must not be null");
    requireNonNull(expectedConfidence, "expectedConfidence must not be null");
    if (learningDivider < 1) {
      throw new IllegalArgumentException("Learning divider must be positive");
    }
    if (inputVector == null) {
      throw new IllegalArgumentException("Input vector must not be null");
    }
    if (this.inputSize != inputVector.length) {
      throw new IllegalArgumentException(
          "Wrong input size: " + this.inputSize + " != " + inputVector.length);
    }
    final int current = this.calc(inputVector);
    final int diff = signedGap(current, expectedConfidence);
    if (diff == 0) {
      return;
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
    requireNonNull(learnStrategy, "learnStrategy must not be null");
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
    if (inputs == null) {
      throw new IllegalArgumentException("Inputs must not be null");
    }
    if (inputs.length != this.inputSize) {
      throw new IllegalArgumentException(
          "Wrong input size: " + this.inputSize + " != " + inputs.length);
    }
    return this.calc(0, inputs);
  }

  /**
   * Sum of the cells addressed by the tail of {@code inputs} that starts at {@code offset}.
   *
   * @param offset index of the symbol for row 0
   * @param inputs symbols; the tail from {@code offset} must cover every row
   * @return signed sum of those cells
   * @throws IllegalArgumentException when {@code offset} is negative, the tail is short,
   *                                  or a symbol is outside its row
   */
  public int calc(final int offset, final int[] inputs) {
    if (inputs == null) {
      throw new IllegalArgumentException("Inputs must not be null");
    }
    if (offset < 0 || inputs.length - offset < this.inputSize) {
      throw new IllegalArgumentException(
          "Unexpected inputs size from offset " + offset + ": " + inputs.length);
    }
    int acc = 0;
    int offsetRow = 0;
    for (int i = 0; i < this.inputSize; i++) {
      final int value = inputs[i + offset];
      if (value < 0 || value >= this.rowLength) {
        throw new IllegalArgumentException(
            "Input " + i + " has value " + value + " outside 0.." + (this.rowLength - 1));
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

  private static final class TableSpec {
    private final int inputSize;
    private final int rowLength;
    private final byte[] table;

    private TableSpec(final int inputSize, final int rowLength, final byte[] table) {
      this.inputSize = inputSize;
      this.rowLength = rowLength;
      this.table = table;
    }

    private static TableSpec ofDefaultRandom(final int inputSize, final int maxInputValue) {
      final int validInputs = requirePositiveInputs(inputSize);
      final int validRowLength = computeRowLength(maxInputValue);
      final byte[] table = new byte[computeTableLength(validInputs, validRowLength)];
      fillByPseudoRnd(table);
      return new TableSpec(validInputs, validRowLength, table);
    }

    private static TableSpec ofRandom(
        final int inputSize,
        final int maxInputValue,
        final Random random) {
      requireNonNull(random, "random must not be null");
      final int validInputs = requirePositiveInputs(inputSize);
      final int validRowLength = computeRowLength(maxInputValue);
      final byte[] table = new byte[computeTableLength(validInputs, validRowLength)];
      random.nextBytes(table);
      return new TableSpec(validInputs, validRowLength, table);
    }
  }
}
