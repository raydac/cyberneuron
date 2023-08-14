package com.igormaznitsa.cyberneuron;

final class ByteCyberNeuron extends CyberNeuron {

    private final byte [] table;
    public ByteCyberNeuron(
            final int inputs,
            final int maxValue
    ) {
        super(inputs, maxValue);
        this.table = new byte[inputs * this.rowLength];
    }

    @Override
    protected int getTableValue(final int index) {
        return this.table[index];
    }

    @Override
    protected int getMaxTableValue() {
        return Byte.MAX_VALUE;
    }

    @Override
    protected int getMinTableValue() {
        return Byte.MIN_VALUE;
    }

    @Override
    protected void setTableValue(final int index, final int value) {
        this.table[index] = (byte) value;
    }
}
