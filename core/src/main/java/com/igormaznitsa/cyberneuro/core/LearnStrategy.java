package com.igormaznitsa.cyberneuro.core;

public enum LearnStrategy {
    RANDOM((neuron, inputs, modifier) -> {
        int simpleModifier = modifier;
        if (simpleModifier >= 0) {
            for (int m = 0; m < simpleModifier; m++) {
                final int rowNumber = neuron.getRnd().nextInt(neuron.getInputSize());
                final int tableIndex = neuron.getRowLength() * rowNumber + inputs[rowNumber];
                int value = neuron.getTableValue(tableIndex);
                if (value < Byte.MAX_VALUE) {
                    value++;
                    neuron.setTableValue(tableIndex, value);
                }
            }
        } else {
            simpleModifier = Math.abs(simpleModifier);
            for (int m = 0; m < simpleModifier; m++) {
                final int rowNumber = neuron.getRnd().nextInt(neuron.getInputSize());
                final int tableIndex = neuron.getRowLength() * rowNumber + inputs[rowNumber];
                int value = neuron.getTableValue(tableIndex);
                if (value > Byte.MIN_VALUE) {
                    value--;
                    neuron.setTableValue(tableIndex, value);
                }
            }
        }
    });

    private final TripleConsumer<CyberNeuron, int[], Integer> changer;

    LearnStrategy(final TripleConsumer<CyberNeuron, int[], Integer> changer) {
        this.changer = changer;
    }

    public void d(final CyberNeuron neuron, final int[] inputs, final int modifier) {
        this.changer.accept(neuron, inputs, modifier);
    }
}
