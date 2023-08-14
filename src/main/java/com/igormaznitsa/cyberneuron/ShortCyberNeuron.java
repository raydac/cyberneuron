package com.igormaznitsa.cyberneuron;

final class ShortCyberNeuron extends CyberNeuron {

    private final short [] table;
    public ShortCyberNeuron(
            final int inputs,
            final int maxValue) {
        super(inputs, maxValue);
        this.table = new short[inputs * this.rowLength];
    }

    @Override
    protected int getMaxTableValue() {
        return Short.MAX_VALUE;
    }

    @Override
    protected int getMinTableValue() {
        return Short.MIN_VALUE;
    }

    @Override
    protected int getTableValue(final int index) {
        return this.table[index];
    }

    @Override
    protected void setTableValue(final int index, final int value) {
        this.table[index] = (short) value;
    }
}
