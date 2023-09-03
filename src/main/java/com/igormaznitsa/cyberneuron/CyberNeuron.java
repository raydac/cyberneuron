package com.igormaznitsa.cyberneuron;

import java.lang.reflect.Array;
import java.util.Random;

import static java.util.Objects.requireNonNull;

public class CyberNeuron {

    protected static final int THRESHOLD_MIN = Byte.MAX_VALUE / 5;
    protected static final int THRESHOLD_MAX = Byte.MAX_VALUE - THRESHOLD_MIN;
    private static final Random DEFAULT_RND = new Random(12345);
    protected final Random rnd;
    protected final int inputSize;
    protected final int maxValue;
    protected final int rowLength;
    private final byte[] table;

    public CyberNeuron(
            final int inputLength,
            final int maxInputValue,
            final Random rnd
    ) {
        this.inputSize = inputLength;
        this.maxValue = maxInputValue;
        this.rowLength = this.maxValue + 1;
        this.table = new byte[inputLength * this.rowLength];
        this.rnd = requireNonNull(rnd);
    }

    public static CyberNeuron of(
            final int inputLength,
            final int maxValue
    ) {
        return CyberNeuron.of(inputLength, maxValue, DEFAULT_RND);
    }

    public static CyberNeuron of(
            final int inputLength,
            final int maxValue,
            final Random rnd
    ) {
        if (inputLength <= 0) throw new IllegalArgumentException("Number of inputs must be positive one");
        if (maxValue < 0) throw new IllegalArgumentException("Max value must not be negative one");
        return new CyberNeuron(inputLength, maxValue, rnd);
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

    protected void assertValue(final int value) {
        if (value < 0 || value > this.maxValue)
            throw new IllegalArgumentException("Unexpected value, must be in range 0.." + this.maxValue);
    }

    public void fillRandom() {
        for (int i = 0; i < (this.inputSize * this.rowLength); i++) {
            this.setTableValue(i, this.rnd.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE));
        }
    }

    private void assertArraySize(final Object array) {
        final int expected = this.rowLength * this.inputSize;
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
        if (this.inputSize != inputs.length)
            throw new IllegalArgumentException("Wrong input length: " + this.inputSize + " != " + inputs.length);
        final int output = this.calc(inputs);
        if (output > THRESHOLD_MAX) return;
        final int modifier = THRESHOLD_MAX - output;
        this.changeNeuronStats(inputs, modifier);
    }

    public void remove(final int[] inputs) {
        if (this.inputSize != inputs.length)
            throw new IllegalArgumentException("Wrong input length: " + this.inputSize + " != " + inputs.length);

        final int output = this.calc(inputs);
        if (output <= THRESHOLD_MIN) return;

        final int modifier = THRESHOLD_MIN - output;
        this.changeNeuronStats(inputs, modifier);
    }

    private void changeNeuronStats(final int[] inputs, final int modifier) {
        if (modifier >= 0) {
            for (int m = 0; m < modifier; m++) {
                final int rowNumber = this.rnd.nextInt(this.inputSize);
                final int tableIndex = this.rowLength * rowNumber + inputs[rowNumber];
                int value = this.getTableValue(tableIndex);
                if (value < Byte.MAX_VALUE) {
                    value++;
                    this.setTableValue(tableIndex, value);
                }
            }
        } else {
            for (int m = 0; m < Math.abs(modifier); m++) {
                final int rowNumber = this.rnd.nextInt(this.inputSize);
                final int tableIndex = this.rowLength * rowNumber + inputs[rowNumber];
                int value = this.getTableValue(tableIndex);
                if (value > Byte.MIN_VALUE) {
                    value--;
                    this.setTableValue(tableIndex, value);
                }
            }
        }
    }

    public ConfidenceDegree check(final int[] inputs) {
        return this.check(0, inputs);
    }

    public ConfidenceDegree check(final int offset, final int[] inputs) {
        final int calculated = calc(offset, inputs);
        if (calculated > THRESHOLD_MAX) return ConfidenceDegree.YES;
        if (calculated > THRESHOLD_MAX / 2) return ConfidenceDegree.MAY_BE_YES;
        if (calculated > THRESHOLD_MIN) return ConfidenceDegree.MAY_BE_NO;
        return ConfidenceDegree.NO;
    }

    public int calc(final int[] inputs) {
        return this.calc(0, inputs);
    }

    public int calc(final int offset, final int[] inputs) {
        if (inputs.length - offset < this.inputSize) {
            throw new IllegalArgumentException("Unexpected inputs length: " + (inputs.length - offset));
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
        buffer.append("CyberNeuron: [");
        int offset = 0;
        for (int i = 0; i < this.inputSize; i++) {
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
