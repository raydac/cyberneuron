package com.igormaznitsa.cyberneuro.core;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * How one teaching step spends a sum correction on the cells the sample addressed.
 *
 * <p>Each step adds or subtracts one in a single addressed cell, and stops at the
 * signed-byte ends. Cells the sample did not address stay as they are.
 */
public enum LearnStrategy {

  /**
   * Walks rows from the starting offset, one step per unsaturated row, then wraps
   * until the steps are spent or all addressed cells saturate.
   */
  SEQUENTIAL(LearnStrategy::applySequential),

  /**
   * Picks a random unsaturated row for every step until the steps are spent
   * or all addressed cells saturate.
   */
  RANDOM(LearnStrategy::applyRandom);

  private final TripleConsumer<CyberNeuron, int[], Integer> changer;

  LearnStrategy(final TripleConsumer<CyberNeuron, int[], Integer> changer) {
    this.changer = changer;
  }

  private static void validateCall(final CyberNeuron neuron, final int[] inputs) {
    requireNonNull(neuron, "neuron must not be null");
    requireNonNull(inputs, "inputs must not be null");
    neuron.assertNonLocked();
    final int inputSize = neuron.getInputSize();
    if (inputs.length != inputSize) {
      throw new IllegalArgumentException(
          "Wrong input size: " + inputSize + " != " + inputs.length);
    }
    final int rowLength = neuron.getRowLength();
    for (int row = 0; row < inputSize; row++) {
      final int symbol = inputs[row];
      if (symbol < 0 || symbol >= rowLength) {
        throw new IllegalArgumentException(
            "Input " + row + " has value " + symbol + " outside 0.." + (rowLength - 1));
      }
    }
  }

  private static void applySequential(
      final CyberNeuron neuron,
      final int[] inputs,
      final int modifier) {
    validateCall(neuron, inputs);
    if (modifier == 0) {
      return;
    }
    final boolean increment = modifier > 0;
    long remaining = Math.abs((long) modifier);
    final int inputSize = neuron.getInputSize();
    final int rowLength = neuron.getRowLength();
    int row = (int) (remaining % inputSize);
    int skippedInRow = 0;
    while (remaining > 0L && skippedInRow < inputSize) {
      final int tableIndex = rowLength * row + inputs[row];
      row = (row + 1) % inputSize;
      if (stepCell(neuron, tableIndex, increment)) {
        remaining--;
        skippedInRow = 0;
      } else {
        skippedInRow++;
      }
    }
  }

  private static void applyRandom(
      final CyberNeuron neuron,
      final int[] inputs,
      final int modifier) {
    validateCall(neuron, inputs);
    if (modifier == 0) {
      return;
    }
    final boolean increment = modifier > 0;
    long remaining = Math.abs((long) modifier);
    final int inputSize = neuron.getInputSize();
    final int rowLength = neuron.getRowLength();
    final int[] activeIndices = new int[inputSize];
    int activeCount = 0;
    for (int row = 0; row < inputSize; row++) {
      final int tableIndex = rowLength * row + inputs[row];
      if (canStep(neuron.getTableValue(tableIndex), increment)) {
        activeIndices[activeCount++] = tableIndex;
      }
    }
    final ThreadLocalRandom random = ThreadLocalRandom.current();
    while (remaining > 0L && activeCount > 0) {
      final int pick = random.nextInt(activeCount);
      final int tableIndex = activeIndices[pick];
      stepCell(neuron, tableIndex, increment);
      remaining--;
      if (!canStep(neuron.getTableValue(tableIndex), increment)) {
        activeIndices[pick] = activeIndices[activeCount - 1];
        activeCount--;
      }
    }
  }

  private static boolean canStep(final int value, final boolean increment) {
    return increment ? value < Byte.MAX_VALUE : value > Byte.MIN_VALUE;
  }

  private static boolean stepCell(
      final CyberNeuron neuron,
      final int tableIndex,
      final boolean increment) {
    final int value = neuron.getTableValue(tableIndex);
    if (!canStep(value, increment)) {
      return false;
    }
    neuron.setTableValue(tableIndex, increment ? value + 1 : value - 1);
    return true;
  }

  /**
   * Rewrites cells of {@code neuron} addressed by {@code inputs}.
   *
   * @param neuron unlocked neuron whose table changes
   * @param inputs one in-range symbol per row; only those cells are eligible
   * @param modifier step count; positive raises cells, negative lowers them
   */
  public void accept(final CyberNeuron neuron, final int[] inputs, final int modifier) {
    this.changer.accept(neuron, inputs, modifier);
  }
}
