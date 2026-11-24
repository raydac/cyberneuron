package com.igormaznitsa.cyberneuro.core;

import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.YES;
import static com.igormaznitsa.cyberneuro.core.LearnStrategy.SEQUENTIAL;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Two-layer net of joint substitution tables, grown without a gradient.
 *
 * <p>Each detector is a {@link CyberNeuron} with one input: the bits named by
 * its sources, packed into a single index. A cell is written with
 * {@link LearnStrategy#SEQUENTIAL} only when the majority of samples that hit
 * it are positive. The root is another joint table over the yes/no bits of
 * those detectors. A candidate detector is kept only when the root's
 * {@link #recognitionShare(List)} rises. Search stops at a perfect score, at
 * the detector limit, or when no tuple improves the score.
 *
 * <p>Not thread-safe. Growing mutates this tree.
 */
public final class CyberTree {

  private static final int MAX_ARITY = 12;
  private static final long MAX_SOURCE_COMBINATIONS = 100_000L;

  private final int bitCount;
  private final List<Detector> detectors = new ArrayList<>();
  private CyberNeuron root;

  private CyberTree(final int bitCount) {
    this.bitCount = bitCount;
  }

  /**
   * Empty tree over {@code bitCount} boolean features.
   *
   * @param bitCount feature width; must be positive
   * @return a tree that rejects every sample until {@link #grow(List, Growth)}
   * @throws IllegalArgumentException when {@code bitCount} is not positive
   */
  public static CyberTree ofBitCount(final int bitCount) {
    if (bitCount < 1) {
      throw new IllegalArgumentException("Bit count must be positive");
    }
    return new CyberTree(bitCount);
  }

  private static CyberNeuron trainByMajority(
      final int arity,
      final int[] indexes,
      final boolean[] labels) {
    final CyberNeuron neuron = CyberNeuron.of(1, (1 << arity) - 1);
    neuron.fill(new byte[neuron.getRowLength()]);
    final int[] positives = new int[neuron.getRowLength()];
    final int[] negatives = new int[neuron.getRowLength()];
    for (int i = 0; i < indexes.length; i++) {
      if (labels[i]) {
        positives[indexes[i]]++;
      } else {
        negatives[indexes[i]]++;
      }
    }
    for (int index = 0; index < positives.length; index++) {
      if (positives[index] > negatives[index]) {
        neuron.teach(new int[] {index}, SEQUENTIAL, YES);
      }
    }
    return neuron;
  }

  private static int packBits(final boolean[] bits, final int[] sources) {
    int packed = 0;
    for (int i = 0; i < sources.length; i++) {
      if (bits[sources[i]]) {
        packed |= 1 << i;
      }
    }
    return packed;
  }

  private static long combinationCount(final int n, final int k) {
    long result = 1L;
    for (int i = 1; i <= k; i++) {
      final int factor = n - k + i;
      if (result > Long.MAX_VALUE / factor) {
        return Long.MAX_VALUE;
      }
      result = result * factor / i;
    }
    return result;
  }

  /**
   * How many first-layer detectors have been committed. The root is not included.
   *
   * @return detector count, zero before a successful {@link #grow(List, Growth)}
   */
  public int detectorCount() {
    return this.detectors.size();
  }

  /**
   * Adds detectors until the score is perfect, the limit is reached, or no
   * tuple of {@code growth}'s arity improves the score. Later calls continue
   * from the detectors already stored.
   *
   * @param examples labeled bit vectors, all of this tree's width, none null
   * @param growth   arity of each joint table and the maximum number of detectors
   * @return {@link #recognitionShare(List)} after growth, from 0 to 1
   * @throws IllegalArgumentException when {@code examples} is empty, a vector
   *                                  has the wrong width, or {@code growth} is unfinished or too wide to search
   * @throws NullPointerException     when {@code examples} or {@code growth} is null
   */
  public double grow(final List<Example> examples, final Growth growth) {
    requireNonNull(growth, "growth must not be null");
    this.requireExamples(examples);
    this.requireGrowth(growth);

    while (this.detectors.size() < growth.detectorLimit
        && this.recognitionShare(examples) < 1.0d) {
      final Detector chosen = this.chooseDetector(examples, growth.arity);
      if (chosen == null) {
        break;
      }
      this.detectors.add(chosen);
      this.root = this.trainRoot(this.detectors, examples);
    }

    return this.recognitionShare(examples);
  }

  /**
   * Whether the root table recognizes {@code bits}. An untrained tree rejects
   * every sample.
   *
   * @param bits feature vector of this tree's width
   * @return {@code true} when the root sum is in the recognized half of the scale
   * @throws IllegalArgumentException when {@code bits} has the wrong width
   * @throws NullPointerException     when {@code bits} is null
   */
  public boolean accepts(final boolean[] bits) {
    this.requireBits(bits);
    if (this.root == null) {
      return false;
    }
    return this.root.matches(new int[] {this.packFires(this.detectors, bits)});
  }

  /**
   * Balanced recognition score from the cyberneuron-tree paper.
   * With true-positive, false-positive, false-negative and true-negative counts,
   * and {@code k = negatives / positives}, the score is
   * {@code (k * truePositive + trueNegative) / (k * truePositive + trueNegative
   * + k * falsePositive + falseNegative)}. An empty tree predicts every sample negative.
   *
   * @param examples labeled bit vectors of this tree's width
   * @return score from 0 to 1
   * @throws IllegalArgumentException when {@code examples} is empty or a width differs
   * @throws NullPointerException     when {@code examples} or an element is null
   */
  public double recognitionShare(final List<Example> examples) {
    this.requireExamples(examples);
    return this.shareOf(this.detectors, this.root, examples);
  }

  /**
   * Detector source indexes, in commit order. Useful when a score is short of 1.
   *
   * @return {@code CyberTree[]} before any detector, otherwise {@code D0[...], D1[...]}
   */
  public String asText() {
    if (this.detectors.isEmpty()) {
      return "CyberTree[]";
    }
    final StringBuilder builder = new StringBuilder("CyberTree[");
    for (int i = 0; i < this.detectors.size(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append('D').append(i).append(Arrays.toString(this.detectors.get(i).sources));
    }
    builder.append(']');
    return builder.toString();
  }

  private Detector chooseDetector(final List<Example> examples, final int arity) {
    Detector chosen = null;
    double best = this.recognitionShare(examples);

    for (final int[] sources : this.sourceCombinations(arity)) {
      if (this.hasDetector(sources)) {
        continue;
      }
      final Detector candidate = this.trainDetector(sources, examples);
      final List<Detector> trial = new ArrayList<>(this.detectors);
      trial.add(candidate);
      final double share = this.shareOf(trial, this.trainRoot(trial, examples), examples);
      if (share > best) {
        best = share;
        chosen = candidate;
      }
    }

    return chosen;
  }

  private boolean hasDetector(final int[] sources) {
    return this.detectors.stream()
        .anyMatch(detector -> Arrays.equals(detector.sources, sources));
  }

  private Detector trainDetector(final int[] sources, final List<Example> examples) {
    final int[] indexes = new int[examples.size()];
    final boolean[] labels = new boolean[examples.size()];
    for (int i = 0; i < examples.size(); i++) {
      final Example example = examples.get(i);
      indexes[i] = packBits(example.bits, sources);
      labels[i] = example.positive;
    }
    return new Detector(trainByMajority(sources.length, indexes, labels), sources);
  }

  private CyberNeuron trainRoot(final List<Detector> detectors, final List<Example> examples) {
    final int[] indexes = new int[examples.size()];
    final boolean[] labels = new boolean[examples.size()];
    for (int i = 0; i < examples.size(); i++) {
      final Example example = examples.get(i);
      indexes[i] = this.packFires(detectors, example.bits);
      labels[i] = example.positive;
    }
    return trainByMajority(detectors.size(), indexes, labels);
  }

  private int packFires(final List<Detector> detectors, final boolean[] bits) {
    int packed = 0;
    for (int i = 0; i < detectors.size(); i++) {
      if (detectors.get(i).fires(bits)) {
        packed |= 1 << i;
      }
    }
    return packed;
  }

  private List<int[]> sourceCombinations(final int arity) {
    final List<int[]> combinations = new ArrayList<>();
    final int[] cursor = new int[arity];
    for (int i = 0; i < arity; i++) {
      cursor[i] = i;
    }
    boolean more = true;
    while (more) {
      combinations.add(cursor.clone());
      more = this.advance(cursor);
    }
    return combinations;
  }

  private boolean advance(final int[] cursor) {
    final int arity = cursor.length;
    int pivot = arity - 1;
    while (pivot >= 0 && cursor[pivot] == this.bitCount - arity + pivot) {
      pivot--;
    }
    if (pivot < 0) {
      return false;
    }
    cursor[pivot]++;
    for (int next = pivot + 1; next < arity; next++) {
      cursor[next] = cursor[next - 1] + 1;
    }
    return true;
  }

  private double shareOf(
      final List<Detector> detectors,
      final CyberNeuron root,
      final List<Example> examples) {
    final Tally tally = new Tally();
    for (final Example example : examples) {
      tally.record(example.positive, this.fires(detectors, root, example.bits));
    }
    return tally.share();
  }

  private boolean fires(
      final List<Detector> detectors,
      final CyberNeuron root,
      final boolean[] bits) {
    if (root == null) {
      return false;
    }
    return root.matches(new int[] {this.packFires(detectors, bits)});
  }

  private void requireExamples(final List<Example> examples) {
    requireNonNull(examples, "examples must not be null");
    if (examples.isEmpty()) {
      throw new IllegalArgumentException("Examples must not be empty");
    }
    for (final Example example : examples) {
      requireNonNull(example, "example must not be null");
      if (example.bitCount() != this.bitCount) {
        throw new IllegalArgumentException(
            "Example width " + example.bitCount() + " does not match " + this.bitCount);
      }
    }
  }

  private void requireBits(final boolean[] bits) {
    requireNonNull(bits, "bits must not be null");
    if (bits.length != this.bitCount) {
      throw new IllegalArgumentException(
          "Bit width " + bits.length + " does not match " + this.bitCount);
    }
  }

  private void requireGrowth(final Growth growth) {
    if (growth.detectorLimit < 1) {
      throw new IllegalArgumentException("Detector limit is not set");
    }
    if (growth.detectorLimit > MAX_ARITY) {
      throw new IllegalArgumentException("Detector limit must be at most " + MAX_ARITY);
    }
    if (growth.arity > this.bitCount) {
      throw new IllegalArgumentException("Arity must be inside 1.." + this.bitCount);
    }
    if (growth.arity > MAX_ARITY) {
      throw new IllegalArgumentException("Arity must be at most " + MAX_ARITY);
    }
    if (combinationCount(this.bitCount, growth.arity) > MAX_SOURCE_COMBINATIONS) {
      throw new IllegalArgumentException(
          "Too many source combinations for arity " + growth.arity);
    }
  }

  /**
   * One labeled bit vector. The stored bits are a copy of the array passed to {@link #of}.
   */
  public static final class Example {
    private final boolean[] bits;
    private final boolean positive;

    private Example(final boolean[] bits, final boolean positive) {
      this.bits = bits;
      this.positive = positive;
    }

    /**
     * Labeled sample. {@code bits} is copied.
     *
     * @param positive {@code true} when the vector belongs to the recognized class
     * @param bits     feature vector; must contain at least one bit
     * @return a sample independent of later changes to {@code bits}
     * @throws IllegalArgumentException when {@code bits} is null or empty
     */
    public static Example of(final boolean positive, final boolean... bits) {
      if (bits == null || bits.length == 0) {
        throw new IllegalArgumentException("Example must contain bits");
      }
      return new Example(bits.clone(), positive);
    }

    /**
     * Class label supplied at construction.
     *
     * @return {@code true} for the recognized class
     */
    public boolean isPositive() {
      return this.positive;
    }

    /**
     * Width of this vector.
     *
     * @return number of bits
     */
    public int bitCount() {
      return this.bits.length;
    }

    /**
     * Copy of the feature vector.
     *
     * @return a new array; mutating it does not change this sample
     */
    public boolean[] bits() {
      return this.bits.clone();
    }
  }

  /**
   * Search bounds for {@link CyberTree#grow(List, Growth)}. Build it with
   * {@link #arity(int)} and then {@link #detectors(int)}. An arity without a
   * detector limit is rejected by grow.
   */
  public static final class Growth {
    private final int arity;
    private final int detectorLimit;

    private Growth(final int arity, final int detectorLimit) {
      this.arity = arity;
      this.detectorLimit = detectorLimit;
    }

    /**
     * Starts a growth spec. Call {@link #detectors(int)} before growing.
     *
     * @param arity how many bits each joint table reads; must be positive
     * @return spec that does not yet name a detector limit
     * @throws IllegalArgumentException when {@code arity} is not positive
     */
    public static Growth arity(final int arity) {
      if (arity < 1) {
        throw new IllegalArgumentException("Arity must be positive");
      }
      return new Growth(arity, 0);
    }

    /**
     * Caps how many first-layer detectors {@link CyberTree#grow(List, Growth)} may add.
     *
     * @param detectorLimit maximum detectors, from 1 through 12
     * @return spec with this arity and limit
     * @throws IllegalArgumentException when {@code detectorLimit} is not positive
     */
    public Growth detectors(final int detectorLimit) {
      if (detectorLimit < 1) {
        throw new IllegalArgumentException("Detector limit must be positive");
      }
      return new Growth(this.arity, detectorLimit);
    }
  }

  private static final class Detector {
    private final CyberNeuron neuron;
    private final int[] sources;

    private Detector(final CyberNeuron neuron, final int[] sources) {
      this.neuron = neuron;
      this.sources = sources.clone();
    }

    private boolean fires(final boolean[] bits) {
      return this.neuron.matches(new int[] {packBits(bits, this.sources)});
    }
  }

  private static final class Tally {
    private int positiveExamples;
    private int negativeExamples;
    private int truePositive;
    private int falsePositive;
    private int falseNegative;
    private int trueNegative;

    private void record(final boolean positive, final boolean predicted) {
      if (positive) {
        this.positiveExamples++;
        if (predicted) {
          this.truePositive++;
        } else {
          this.falseNegative++;
        }
        return;
      }
      this.negativeExamples++;
      if (predicted) {
        this.falsePositive++;
      } else {
        this.trueNegative++;
      }
    }

    private double share() {
      if (this.positiveExamples == 0) {
        return this.falsePositive == 0 ? 1.0d : 0.0d;
      }
      if (this.negativeExamples == 0) {
        return this.falseNegative == 0 ? 1.0d : 0.0d;
      }
      final double balance = (double) this.negativeExamples / (double) this.positiveExamples;
      final double good = balance * this.truePositive + this.trueNegative;
      final double bad = balance * this.falsePositive + this.falseNegative;
      final double total = good + bad;
      return total == 0.0d ? 0.0d : good / total;
    }
  }
}
