package com.igormaznitsa.cyberneuro.core;

import java.util.Random;

public enum LearnStrategy {

  SEQUENTIAL((neuron, inputs, modifier) -> {
    int simpleModifier = modifier;
    final boolean increment = simpleModifier >= 0;
    simpleModifier = Math.abs(simpleModifier);
    int index = simpleModifier % neuron.getInputSize();
    if (simpleModifier >= 0) {
      for (int m = 0; m < simpleModifier; m++) {
        final int rowNumber = index % neuron.getInputSize();
        final int tableIndex = neuron.getRowLength() * rowNumber + inputs[rowNumber];
        int value = neuron.getTableValue(tableIndex);
        if (increment) {
          if (value < Byte.MAX_VALUE) {
            value++;
            neuron.setTableValue(tableIndex, value);
          }
        } else {
          if (value > Byte.MIN_VALUE) {
            value--;
            neuron.setTableValue(tableIndex, value);
          }
        }
        index++;
      }
    }
  }),
  RANDOM((neuron, inputs, modifier) -> {
    int simpleModifier = modifier;
    final boolean increment = simpleModifier >= 0;
    simpleModifier = Math.abs(simpleModifier);
    if (simpleModifier >= 0) {
      for (int m = 0; m < simpleModifier; m++) {
        final int rowNumber = Internal.RND.nextInt(neuron.getInputSize());
        final int tableIndex = neuron.getRowLength() * rowNumber + inputs[rowNumber];
        int value = neuron.getTableValue(tableIndex);
        if (increment) {
          if (value < Byte.MAX_VALUE) {
            value++;
            neuron.setTableValue(tableIndex, value);
          }
        } else {
          if (value > Byte.MIN_VALUE) {
            value--;
            neuron.setTableValue(tableIndex, value);
          }
        }
      }
    }
  });

  private final TripleConsumer<CyberNeuron, int[], Integer> changer;

  LearnStrategy(final TripleConsumer<CyberNeuron, int[], Integer> changer) {
    this.changer = changer;
  }

  public void accept(final CyberNeuron neuron, final int[] inputs, final int modifier) {
    this.changer.accept(neuron, inputs, modifier);
  }

  private static final class Internal {
    private static final Random RND = new Random();
  }
}
