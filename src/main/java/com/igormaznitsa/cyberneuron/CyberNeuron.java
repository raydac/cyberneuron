package com.igormaznitsa.cyberneuron;

import java.lang.reflect.Array;
import java.util.Random;

public abstract class CyberNeuron {

    private static final Random RND_GEN = new Random(12345L);
    protected final int inputLength;
    protected final int maxValue;
    protected final int rowLength;
    protected final int thresholdMin;
    protected final int thresholdMax;

    protected CyberNeuron(
            final int inputLength,
            final int maxValue
    ) {
        this.inputLength = inputLength;
        this.maxValue = maxValue;

        this.thresholdMin = this.getMaxTableValue() / 5;
        this.thresholdMax = this.getMaxTableValue() - this.thresholdMin;

        this.rowLength = this.maxValue + 1;
    }

    public static CyberNeuron make(
            final int inputLength,
            final int maxValue,
            final boolean doublePrecision
    ) {
        if (inputLength <= 0) throw new IllegalArgumentException("Number of inputs must be positive one");
        if (maxValue < 0) throw new IllegalArgumentException("Max value must not be negative one");

        if (doublePrecision) {
            return new ShortCyberNeuron(inputLength, maxValue);
        } else {
            return new ByteCyberNeuron(inputLength, maxValue);
        }
    }

    protected abstract int getMaxTableValue();

    protected abstract int getMinTableValue();

    public int getThresholdMin() {
        return this.thresholdMin;
    }

    public int getThresholdMax() {
        return this.thresholdMax;
    }

    protected void assertValue(final int value) {
        if (value < 0 || value > this.maxValue)
            throw new IllegalArgumentException("Unexpected value, must be in range 0.." + this.maxValue);
    }

    public void fillRandom() {
        for (int i = 0; i < (this.inputLength * this.rowLength); i++) {
            this.setTableValue(i, RND_GEN.nextInt(this.getMinTableValue(), this.getMaxTableValue()));
        }
    }

    private void assertArraySize(final Object array) {
        final int expected = this.rowLength * this.inputLength;
        final int provided = Array.getLength(array);
        if (provided != expected) {
            throw new IllegalArgumentException("Expected size of array is " + expected + " but provided " + provided);
        }
    }

    public void fill(final byte[] values) {
        assertArraySize(values);
        for (int i = 0; i < values.length; i++) {
            this.setTableValue(i, values[i]);
        }
    }

    public void fill(final short[] values) {
        assertArraySize(values);
        for (int i = 0; i < values.length; i++) {
            this.setTableValue(i, values[i]);
        }
    }

    public void fill(final int[] values) {
        assertArraySize(values);
        for (int i = 0; i < values.length; i++) {
            this.setTableValue(i, values[i]);
        }
    }

    public void add(final int[] inputs) {
        if (this.inputLength != inputs.length)
            throw new IllegalArgumentException("Wrong input length: " + this.inputLength + " != " + inputs.length);
        final int output = this.calc(inputs);
        if (output > this.getThresholdMax()) return;

        final int modifier = this.getThresholdMax() - output;
        this.changeNeuronStats(inputs, modifier);
    }

    public void remove(final int[] inputs) {
        if (this.inputLength != inputs.length)
            throw new IllegalArgumentException("Wrong input length: " + this.inputLength + " != " + inputs.length);

        final int output = this.calc(inputs);
        if (output <= this.getThresholdMin()) return;


        final int modifier = this.getThresholdMin() - output;
        this.changeNeuronStats(inputs, modifier);
    }

    private void changeNeuronStats(final int[] inputs, final int modifier) {
        if (modifier >= 0) {
            for (int m = 0; m < modifier; m++) {
                final int rowNumber = RND_GEN.nextInt(this.inputLength);
                final int tableIndex = this.rowLength * rowNumber + inputs[rowNumber];
                int value = this.getTableValue(tableIndex);
                if (value < this.getMaxTableValue()) {
                    value++;
                    this.setTableValue(tableIndex, value);
                }
            }
        } else {
            for (int m = 0; m < Math.abs(modifier); m++) {
                final int rowNumber = RND_GEN.nextInt(this.inputLength);
                final int tableIndex = this.rowLength * rowNumber + inputs[rowNumber];
                int value = this.getTableValue(tableIndex);
                if (value > this.getMinTableValue()) {
                    value--;
                    this.setTableValue(tableIndex, value);
                }
            }
        }
    }

    public ConfidenceDegree check(final int[] inputs) {
        final int calculated = calc(inputs);
        if (calculated > this.thresholdMax) return ConfidenceDegree.YES;
        if (calculated > this.getThresholdMax() / 2) return ConfidenceDegree.MAY_BE_YES;
        if (calculated > this.thresholdMin) return ConfidenceDegree.MAY_BE_NO;
        return ConfidenceDegree.NO;
    }

    public int calc(final int[] inputs) {
        if (inputs.length != this.inputLength) {
            throw new IllegalArgumentException("Expected input array length is " + this.inputLength + ": " + inputs.length);
        }
        int acc = 0;
        int offset = 0;
        for (int i = 0; i < this.inputLength; i++) {
            acc += this.getTableValue(offset + inputs[i]);
            offset += this.rowLength;
        }
        return acc;
    }

    protected abstract int getTableValue(int index);

    protected abstract void setTableValue(int index, int value);

    public String asText() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("CyberNeuron: [");
        int offset = 0;
        for (int i = 0; i < this.inputLength; i++) {
            if (i > 0) buffer.append(", ");
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
