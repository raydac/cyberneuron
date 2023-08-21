package com.igormaznitsa.cyberneuron;

import java.util.List;
import java.util.Set;

public class CyberNeuronLayer {

    private final List<CyberNeuron> neurons;
    private final int inputSize;

    protected CyberNeuronLayer(final List<CyberNeuron> neurons) {
        if (Set.copyOf(neurons).size() != neurons.size()) {
            throw new IllegalArgumentException("List must not contains duplicated neurons");
        }
        this.neurons = List.copyOf(neurons);
        this.inputSize = this.neurons.stream().mapToInt(CyberNeuron::getInputSize).sum();
    }

    public static CyberNeuronLayer makeLayer(final List<CyberNeuron> neurons) {
        return new CyberNeuronLayer(neurons);
    }

    public static CyberNeuronLayer makeLayer(final CyberNeuron... neurons) {
        return new CyberNeuronLayer(List.of(neurons));
    }

    public int getInputSize() {
        return this.inputSize;
    }

    public int getOutputSize() {
        return this.neurons.size();
    }

    public ConfidenceDegree[] check(final int[] inputs) {
        if (this.inputSize != inputs.length) {
            throw new IllegalArgumentException("Expected input size: " + this.inputSize);
        }
        final ConfidenceDegree[] result = new ConfidenceDegree[this.neurons.size()];
        int offset = 0;
        int index = 0;
        for (final CyberNeuron n : this.neurons) {
            result[index++] = n.check(offset, inputs);
            offset += n.getInputSize();
        }
        return result;
    }

    public int[] calc(final int[] inputs) {
        if (this.inputSize != inputs.length) {
            throw new IllegalArgumentException("Expected input size: " + this.inputSize);
        }
        final int[] result = new int[this.neurons.size()];
        int offset = 0;
        int index = 0;
        for (final CyberNeuron n : this.neurons) {
            result[index++] = n.calc(offset, inputs);
            offset += n.getInputSize();
        }
        return result;
    }
}
