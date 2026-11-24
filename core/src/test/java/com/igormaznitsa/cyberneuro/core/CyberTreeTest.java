package com.igormaznitsa.cyberneuro.core;

import static com.igormaznitsa.cyberneuro.core.CyberTree.Growth.arity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.igormaznitsa.cyberneuro.core.CyberTree.Example;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CyberTreeTest {

  @Test
  void learnsXorWithOneJointTable() {
    final List<Example> examples = List.of(
        Example.of(false, false, false),
        Example.of(true, false, true),
        Example.of(true, true, false),
        Example.of(false, true, true)
    );
    final CyberTree tree = CyberTree.ofBitCount(2);

    final double share = tree.grow(examples, arity(2).detectors(1));

    assertEquals(1.0d, share, 0.0d);
    assertEquals(1, tree.detectorCount());
    this.assertRecognizes(tree, examples);
  }

  @Test
  void learnsDisjunctionOfConjunctions() {
    final List<Example> examples = this.orOfAnds();
    final CyberTree tree = CyberTree.ofBitCount(4);

    final double share = tree.grow(examples, arity(2).detectors(2));

    assertEquals(1.0d, share, 0.0d, tree.asText());
    assertEquals(2, tree.detectorCount());
    this.assertRecognizes(tree, examples);
  }

  @Test
  void learnsXorOfConjunctionsOnSecondLayer() {
    final List<Example> examples = this.xorOfAnds();
    final CyberTree tree = CyberTree.ofBitCount(4);

    final double share = tree.grow(examples, arity(2).detectors(2));

    assertEquals(1.0d, share, 0.0d, tree.asText());
    assertEquals(2, tree.detectorCount());
    this.assertRecognizes(tree, examples);
  }

  @Test
  void pairTablesDoNotSolveThreeBitParity() {
    final List<Example> examples = this.parity(3);
    final CyberTree tree = CyberTree.ofBitCount(3);

    final double share = tree.grow(examples, arity(2).detectors(3));

    assertTrue(share < 1.0d);
    assertFalse(this.recognizes(tree, examples));
  }

  @Test
  void tripleTableSolvesThreeBitParity() {
    final List<Example> examples = this.parity(3);
    final CyberTree tree = CyberTree.ofBitCount(3);

    final double share = tree.grow(examples, arity(3).detectors(1));

    assertEquals(1.0d, share, 0.0d);
    this.assertRecognizes(tree, examples);
  }

  private void assertRecognizes(final CyberTree tree, final List<Example> examples) {
    assertTrue(this.recognizes(tree, examples), tree.asText());
  }

  private boolean recognizes(final CyberTree tree, final List<Example> examples) {
    for (final Example example : examples) {
      if (tree.accepts(example.bits()) != example.isPositive()) {
        return false;
      }
    }
    return true;
  }

  private List<Example> orOfAnds() {
    final List<Example> examples = new ArrayList<>();
    for (int mask = 0; mask < 16; mask++) {
      final boolean left = (mask & 1) != 0;
      final boolean right = (mask & 2) != 0;
      final boolean low = (mask & 4) != 0;
      final boolean high = (mask & 8) != 0;
      examples.add(Example.of((left && right) || (low && high), left, right, low, high));
    }
    return examples;
  }

  private List<Example> xorOfAnds() {
    final List<Example> examples = new ArrayList<>();
    for (int mask = 0; mask < 16; mask++) {
      final boolean left = (mask & 1) != 0;
      final boolean right = (mask & 2) != 0;
      final boolean low = (mask & 4) != 0;
      final boolean high = (mask & 8) != 0;
      examples.add(Example.of((left && right) ^ (low && high), left, right, low, high));
    }
    return examples;
  }

  private List<Example> parity(final int width) {
    final List<Example> examples = new ArrayList<>();
    for (int mask = 0; mask < (1 << width); mask++) {
      final boolean[] bits = new boolean[width];
      int ones = 0;
      for (int i = 0; i < width; i++) {
        bits[i] = ((mask >> i) & 1) == 1;
        if (bits[i]) {
          ones++;
        }
      }
      examples.add(Example.of((ones & 1) == 1, bits));
    }
    return examples;
  }
}
