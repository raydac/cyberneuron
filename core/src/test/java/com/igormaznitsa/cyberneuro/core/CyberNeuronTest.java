package com.igormaznitsa.cyberneuro.core;

import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.MAY_BE_NO;
import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.MAY_BE_YES;
import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.NO;
import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.YES;
import static com.igormaznitsa.cyberneuro.core.LearnStrategy.RANDOM;
import static com.igormaznitsa.cyberneuro.core.LearnStrategy.SEQUENTIAL;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
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
    assertEquals(MAY_BE_YES, neuron.activateAsConfidence(new int[] {1, 3, 0, 1, 2, 1}).get(0));
  }

  @Test
  void testLearnAnd() {
    final CyberNeuron neuron = CyberNeuron.of(2, 1);

    neuron.teach(new int[] {1, 1}, SEQUENTIAL, YES);
    neuron.teach(new int[] {0, 0}, SEQUENTIAL, NO);
    neuron.teach(new int[] {0, 1}, SEQUENTIAL, NO);
    neuron.teach(new int[] {1, 0}, SEQUENTIAL, NO);

    assertEquals(NO, neuron.activateAsConfidence(new int[] {0, 0}).get(0));
    assertEquals(NO, neuron.activateAsConfidence(new int[] {0, 1}).get(0));
    assertEquals(NO, neuron.activateAsConfidence(new int[] {1, 0}).get(0));
    assertEquals(MAY_BE_YES, neuron.activateAsConfidence(new int[] {1, 1}).get(0));
  }

  @Test
  void testLearnOr() {
    final CyberNeuron neuron = CyberNeuron.of(2, 1);

    neuron.teach(new int[] {0, 1}, SEQUENTIAL, YES);
    neuron.teach(new int[] {1, 1}, SEQUENTIAL, YES);
    neuron.teach(new int[] {0, 0}, SEQUENTIAL, NO);
    neuron.teach(new int[] {1, 0}, SEQUENTIAL, YES);

    assertEquals(MAY_BE_NO, neuron.activateAsConfidence(new int[] {0, 0}).get(0));
    assertEquals(MAY_BE_YES, neuron.activateAsConfidence(new int[] {0, 1}).get(0));
    assertEquals(YES, neuron.activateAsConfidence(new int[] {1, 0}).get(0));
    assertEquals(YES, neuron.activateAsConfidence(new int[] {1, 1}).get(0));
  }

  @Test
  void testLearnNot() {
    final CyberNeuron neuron = CyberNeuron.of(1, 1);

    neuron.teach(new int[] {0}, SEQUENTIAL, YES);
    neuron.teach(new int[] {1}, SEQUENTIAL, NO);

    assertEquals(NO, neuron.activateAsConfidence(new int[] {1}).get(0));
    assertEquals(YES, neuron.activateAsConfidence(new int[] {0}).get(0));
  }

  @Test
  void testSignalIsConfidenceOrdinal() {
    final CyberNeuron neuron = CyberNeuron.of(6, 3);
    neuron.fill(new byte[] {
        0, 15, 0, 0,
        0, 0, 0, 20,
        14, 0, 0, 0,
        0, 18, 0, 0,
        0, 0, 16, 0,
        0, 17, 0, 0
    });

    assertEquals(MAY_BE_YES.ordinal(), neuron.signal(new int[] {1, 3, 0, 1, 2, 1}));
    assertTrue(neuron.matches(new int[] {1, 3, 0, 1, 2, 1}));
  }

  @Test
  void testSignalFeedsAnotherNeuron() {
    final CyberNeuron first = CyberNeuron.of(1, 1);
    first.teach(new int[] {0}, SEQUENTIAL, NO);
    first.teach(new int[] {1}, SEQUENTIAL, YES);

    final int symbol = first.signal(new int[] {1});
    final CyberNeuron second = CyberNeuron.of(1, YES.ordinal());
    second.teach(new int[] {symbol}, SEQUENTIAL, YES);

    assertTrue(symbol >= 0 && symbol <= YES.ordinal());
    assertTrue(second.matches(new int[] {symbol}));
  }

  @Test
  void testLearningDividerScalesStep() {
    final CyberNeuron neuron = CyberNeuron.of(1, 1);
    neuron.fill(new byte[] {0, 0});

    neuron.teach(new int[] {0}, SEQUENTIAL, YES, 4);

    assertEquals(25, neuron.calc(new int[] {0}));
  }

  @Test
  void testRejectsInputSymbolOutsideTable() {
    final CyberNeuron neuron = CyberNeuron.of(1, 1);

    assertThrows(IllegalArgumentException.class, () -> neuron.calc(new int[] {2}));
    assertThrows(IllegalArgumentException.class, () -> neuron.calc(new int[] {-1}));
  }

  @Test
  void testRepeatedTeachingDoesNotSolveXor() {
    final CyberNeuron neuron = CyberNeuron.of(2, 1);
    final int[][] inputs = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
    final boolean[] positive = {false, true, true, false};
    for (int pass = 0; pass < 12; pass++) {
      for (int i = 0; i < inputs.length; i++) {
        neuron.teach(inputs[i], SEQUENTIAL, positive[i] ? YES : NO);
      }
    }

    assertFalse(this.solves(neuron, inputs, positive));
  }

  @Test
  void testRepeatedTeachingDoesNotSolveOrOfAnds() {
    final CyberNeuron neuron = CyberNeuron.of(4, 1);
    final int[][] inputs = new int[16][];
    final boolean[] positive = new boolean[16];
    for (int mask = 0; mask < 16; mask++) {
      inputs[mask] = new int[] {
          mask & 1, (mask >> 1) & 1, (mask >> 2) & 1, (mask >> 3) & 1
      };
      positive[mask] = (mask & 3) == 3 || (mask & 12) == 12;
    }
    for (int pass = 0; pass < 12; pass++) {
      for (int mask = 0; mask < inputs.length; mask++) {
        neuron.teach(inputs[mask], SEQUENTIAL, positive[mask] ? YES : NO);
      }
    }

    assertFalse(this.solves(neuron, inputs, positive));
  }

  @Test
  void testTeachMayBeNoAcrossBoundary25() {
    final CyberNeuron neuron = CyberNeuron.of(1, 1);
    neuron.fill(new byte[] {25, 0});

    assertEquals(NO, neuron.activateAsConfidence(new int[] {0}).get(0));
    neuron.teach(new int[] {0}, SEQUENTIAL, MAY_BE_NO, 1);
    assertEquals(26, neuron.calc(new int[] {0}));
    assertEquals(MAY_BE_NO, neuron.activateAsConfidence(new int[] {0}).get(0));
  }

  @Test
  void testSignedGapAndTeachKeepMultiRowSumsBeyondByteBoundsInBand() {
    final CyberNeuron neuron = CyberNeuron.of(2, 1);
    neuron.fill(new byte[] {-100, 100, -100, 100});

    assertEquals(0, CyberNeuron.signedGap(-200, NO));
    assertEquals(0, CyberNeuron.signedGap(200, YES));

    neuron.teach(new int[] {0, 0}, SEQUENTIAL, NO, 1);
    neuron.teach(new int[] {1, 1}, SEQUENTIAL, YES, 1);

    assertEquals(-200, neuron.calc(new int[] {0, 0}));
    assertEquals(200, neuron.calc(new int[] {1, 1}));
  }

  @Test
  void testLearnStrategiesRedistributeStepsWhenCellsSaturate() {
    final CyberNeuron sequentialNeuron = CyberNeuron.of(2, 1);
    sequentialNeuron.fill(new byte[] {Byte.MAX_VALUE, 0, 0, 0});
    sequentialNeuron.nudge(new int[] {0, 0}, SEQUENTIAL, 20, 1);
    assertEquals(Byte.MAX_VALUE, sequentialNeuron.getTableValue(0));
    assertEquals(20, sequentialNeuron.getTableValue(2));

    final CyberNeuron randomNeuron = CyberNeuron.of(2, 1);
    randomNeuron.fill(new byte[] {Byte.MIN_VALUE, 0, 0, 0});
    randomNeuron.nudge(new int[] {0, 0}, RANDOM, -15, 1);
    assertEquals(Byte.MIN_VALUE, randomNeuron.getTableValue(0));
    assertEquals(-15, randomNeuron.getTableValue(2));
  }

  @Test
  void testLockBlocksAllMutationsUntilUnlocked() {
    final CyberNeuron neuron = CyberNeuron.of(1, 1);
    neuron.fill(new byte[] {0, 0});
    neuron.setLock(true);

    assertTrue(neuron.isLocked());
    assertThrows(IllegalStateException.class, () -> neuron.setTableValue(0, 5));
    assertThrows(IllegalStateException.class, () -> neuron.fill(new byte[] {1, 1}));
    assertThrows(IllegalStateException.class, () -> neuron.teach(new int[] {0}, SEQUENTIAL, YES));
    assertThrows(IllegalStateException.class, () -> neuron.nudge(new int[] {0}, SEQUENTIAL, 5, 1));
    assertThrows(IllegalStateException.class, () -> SEQUENTIAL.accept(neuron, new int[] {0}, 5));

    neuron.setLock(false);
    neuron.setTableValue(0, 7);
    assertEquals(7, neuron.getTableValue(0));
  }

  @Test
  void testValidationAndSeededInitialization() {
    assertThrows(IllegalArgumentException.class, () -> new CyberNeuron(0, 1));
    assertThrows(IllegalArgumentException.class, () -> new CyberNeuron(1, -1));
    assertThrows(IllegalArgumentException.class, () -> new CyberNeuron(1, Integer.MAX_VALUE));
    assertThrows(IllegalArgumentException.class, () -> new CyberNeuron(Integer.MAX_VALUE, 2));

    final CyberNeuron neuron = CyberNeuron.of(2, 1);
    assertThrows(IllegalArgumentException.class, () -> neuron.calc(-1, new int[] {0, 0, 0}));
    assertThrows(IllegalArgumentException.class,
        () -> neuron.nudge(new int[] {0, 2}, RANDOM, 4, 1));
    assertThrows(IllegalArgumentException.class,
        () -> SEQUENTIAL.accept(neuron, new int[] {2, 0}, 2));

    final CyberNeuron seededA = CyberNeuron.of(4, 3, new Random(101L));
    final CyberNeuron seededA2 = CyberNeuron.of(4, 3, new Random(101L));
    final CyberNeuron seededB = CyberNeuron.of(4, 3, new Random(202L));
    final byte[] tableA = new byte[16];
    final byte[] tableA2 = new byte[16];
    final byte[] tableB = new byte[16];
    for (int i = 0; i < 16; i++) {
      tableA[i] = (byte) seededA.getTableValue(i);
      tableA2[i] = (byte) seededA2.getTableValue(i);
      tableB[i] = (byte) seededB.getTableValue(i);
    }
    assertArrayEquals(tableA, tableA2);
    assertFalse(Arrays.equals(tableA, tableB));
  }

  private boolean solves(
      final CyberNeuron neuron,
      final int[][] inputs,
      final boolean[] positive) {
    for (int i = 0; i < inputs.length; i++) {
      if (neuron.matches(inputs[i]) != positive[i]) {
        return false;
      }
    }
    return true;
  }

}