package com.igormaznitsa.cyberneuro.core;

import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.MAY_BE_YES;
import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.NO;
import static com.igormaznitsa.cyberneuro.core.LearnStrategy.SEQUENTIAL;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CyberNeuronTest {

  @Test
  void testCalc() {
    final CyberNeuron neuron = CyberNeuron.of(6, 3);
    neuron.fill(new byte[] {
        0, 15, 0, 0,
        0, 0, 0, 20,
        14, 0, 0, 0,
        0, 18, 0, 0,
        0, 0, 16, 0,
        0, 17, 0, 0
    });
    assertEquals(100L, neuron.calc(new int[] {1, 3, 0, 1, 2, 1}));
    assertEquals(MAY_BE_YES, neuron.check(new int[] {1, 3, 0, 1, 2, 1}));
  }

  @Test
  void testLearnAnd() {
    final CyberNeuron neuron = CyberNeuron.of(2, 1);

    neuron.add(new int[] {1, 1}, SEQUENTIAL);
    neuron.remove(new int[] {0, 0}, SEQUENTIAL);
    neuron.remove(new int[] {0, 1}, SEQUENTIAL);
    neuron.remove(new int[] {1, 0}, SEQUENTIAL);

    assertEquals(NO, neuron.check(new int[] {0, 0}));
    assertEquals(NO, neuron.check(new int[] {0, 1}));
    assertEquals(NO, neuron.check(new int[] {1, 0}));
    assertEquals(MAY_BE_YES, neuron.check(new int[] {1, 1}));
  }

  @Test
  void testLearnOr() {
    final CyberNeuron neuron = CyberNeuron.of(2, 1);

    neuron.add(new int[] {0, 1}, SEQUENTIAL);
    neuron.add(new int[] {1, 0}, SEQUENTIAL);
    neuron.remove(new int[] {0, 0}, SEQUENTIAL);
    neuron.add(new int[] {1, 1}, SEQUENTIAL);

    assertEquals(NO, neuron.check(new int[] {0, 0}));
    assertEquals(MAY_BE_YES, neuron.check(new int[] {0, 1}));
    assertEquals(MAY_BE_YES, neuron.check(new int[] {1, 0}));
    assertEquals(MAY_BE_YES, neuron.check(new int[] {1, 1}));
  }

  @Test
  void testLearnNot() {
    final CyberNeuron neuron = CyberNeuron.of(1, 1);

    neuron.add(new int[] {0}, SEQUENTIAL);
    neuron.remove(new int[] {1}, SEQUENTIAL);

    assertEquals(NO, neuron.check(new int[] {1}));
    assertEquals(MAY_BE_YES, neuron.check(new int[] {0}));
  }


}