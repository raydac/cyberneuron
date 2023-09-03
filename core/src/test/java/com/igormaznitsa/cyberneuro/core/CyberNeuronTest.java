package com.igormaznitsa.cyberneuro.core;

import org.junit.jupiter.api.Test;

import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CyberNeuronTest {

    @Test
    void testCalc() {
        final CyberNeuron neuron = CyberNeuron.of(6, 3);
        neuron.fill(new byte[]{
                0, 15, 0, 0,
                0, 0, 0, 20,
                14, 0, 0, 0,
                0, 18, 0, 0,
                0, 0, 16, 0,
                0, 17, 0, 0
        });
        assertEquals(100L, neuron.calc(new int[]{1, 3, 0, 1, 2, 1}));
        assertEquals(MAY_BE_YES, neuron.check(new int[]{1, 3, 0, 1, 2, 1}));
    }

    @Test
    void testLearnAnd() {
        final CyberNeuron neuron = CyberNeuron.of(2, 1);

        neuron.add(new int[]{1, 1});
        neuron.remove(new int[]{0, 0});
        neuron.remove(new int[]{0, 1});
        neuron.remove(new int[]{1, 0});

        assertEquals(NO, neuron.check(new int[]{0, 0}));
        assertEquals(NO, neuron.check(new int[]{0, 1}));
        assertEquals(NO, neuron.check(new int[]{1, 0}));
        assertEquals(MAY_BE_YES, neuron.check(new int[]{1, 1}));
    }

    @Test
    void testLearnOr() {
        final CyberNeuron neuron = CyberNeuron.of(2, 1);

        neuron.add(new int[]{0, 1});
        neuron.add(new int[]{1, 0});
        neuron.add(new int[]{1, 1});
        neuron.remove(new int[]{0, 0});

        assertEquals(NO, neuron.check(new int[]{0, 0}));
        assertEquals(MAY_BE_YES, neuron.check(new int[]{0, 1}));
        assertEquals(MAY_BE_YES, neuron.check(new int[]{1, 0}));
        assertEquals(YES, neuron.check(new int[]{1, 1}));
    }

    @Test
    void testLearnNot() {
        final CyberNeuron neuron = CyberNeuron.of(1, 1);

        neuron.add(new int[]{0});
        neuron.remove(new int[]{1});

        assertEquals(NO, neuron.check(new int[]{1}));
        assertEquals(MAY_BE_YES, neuron.check(new int[]{0}));
    }


}