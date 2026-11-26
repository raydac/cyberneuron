package com.igormaznitsa.cyberneuro.core;

import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.MAY_BE_YES;
import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.NO;
import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.YES;
import static com.igormaznitsa.cyberneuro.core.LearnStrategy.RANDOM;
import static com.igormaznitsa.cyberneuro.core.LearnStrategy.SEQUENTIAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CyberNetworkTest {

  @Test
  void teachesASingleNeuronAnd() {
    final CyberNeuron neuron = CyberNeuron.of(2, 1);
    final CyberNetwork network = CyberNetwork.externalInputs(2)
        .bind(neuron, 0, 0)
        .bind(neuron, 1, 1)
        .output(neuron)
        .build();

    network.teach(new int[] {1, 1}, List.of(YES), SEQUENTIAL, 1);
    network.teach(new int[] {0, 0}, List.of(NO), SEQUENTIAL, 1);
    network.teach(new int[] {0, 1}, List.of(NO), SEQUENTIAL, 1);
    network.teach(new int[] {1, 0}, List.of(NO), SEQUENTIAL, 1);

    assertEquals(List.of(NO), network.recall(new int[] {0, 0}));
    assertEquals(List.of(NO), network.recall(new int[] {0, 1}));
    assertEquals(List.of(NO), network.recall(new int[] {1, 0}));
    assertEquals(List.of(MAY_BE_YES), network.recall(new int[] {1, 1}));
  }

  @Test
  void scalesHiddenStepsByLayerWeight() {
    assertEquals(4, this.hiddenCellAfter(null));
    assertEquals(4, this.hiddenCellAfter(1));
    assertEquals(12, this.hiddenCellAfter(3));
  }

  @Test
  void rejectsANonPositiveLayerWeight() {
    final CyberNeuron hidden = CyberNeuron.of(1, 1);

    assertThrows(IllegalArgumentException.class,
        () -> CyberNetwork.externalInputs(1).layer(0, hidden));
  }

  @Test
  void teachesNotThroughTwoLayers() {
    final CyberNeuron hidden = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron decision = this.cleared(CyberNeuron.of(1, YES.ordinal()));
    final CyberNetwork network = CyberNetwork.externalInputs(1)
        .bind(hidden, 0, 0)
        .connect(hidden, decision, 0)
        .output(decision)
        .build();

    for (int pass = 0; pass < 30; pass++) {
      network.teach(new int[] {0}, List.of(YES), SEQUENTIAL, 1);
      network.teach(new int[] {1}, List.of(NO), SEQUENTIAL, 1);
    }

    assertEquals(List.of(YES), network.recall(new int[] {0}));
    assertEquals(List.of(NO), network.recall(new int[] {1}));
  }

  @Test
  void teachesNotThroughThreeLayers() {
    final CyberNeuron first = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron second = this.cleared(CyberNeuron.of(1, YES.ordinal()));
    final CyberNeuron decision = this.cleared(CyberNeuron.of(1, YES.ordinal()));
    final CyberNetwork network = CyberNetwork.externalInputs(1)
        .bind(first, 0, 0)
        .connect(first, second, 0)
        .connect(second, decision, 0)
        .output(decision)
        .build();

    for (int pass = 0; pass < 40; pass++) {
      network.teach(new int[] {0}, List.of(YES), SEQUENTIAL, 1);
      network.teach(new int[] {1}, List.of(NO), SEQUENTIAL, 1);
    }

    assertEquals(List.of(YES), network.recall(new int[] {0}));
    assertEquals(List.of(NO), network.recall(new int[] {1}));
  }

  @Test
  void teachesOrAndOnTwoOutputs() {
    final CyberNeuron orLeft = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron orRight = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron andLeft = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron andRight = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron orGate = this.cleared(CyberNeuron.of(2, YES.ordinal()));
    final CyberNeuron andGate = this.cleared(CyberNeuron.of(2, YES.ordinal()));
    final CyberNetwork network = CyberNetwork.externalInputs(2)
        .bind(orLeft, 0, 0)
        .bind(orRight, 0, 1)
        .bind(andLeft, 0, 0)
        .bind(andRight, 0, 1)
        .connect(orLeft, orGate, 0)
        .connect(orRight, orGate, 1)
        .connect(andLeft, andGate, 0)
        .connect(andRight, andGate, 1)
        .output(orGate)
        .output(andGate)
        .build();

    final int[][] samples = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
    final List<List<ConfidenceDegree>> labels = List.of(
        List.of(NO, NO),
        List.of(YES, NO),
        List.of(YES, NO),
        List.of(YES, YES));
    for (int pass = 0; pass < 40; pass++) {
      for (int i = 0; i < samples.length; i++) {
        network.teach(samples[i], labels.get(i), SEQUENTIAL, 1);
      }
    }

    assertEquals(List.of(NO, NO), network.recall(new int[] {0, 0}));
    assertEquals(List.of(YES, NO), network.recall(new int[] {0, 1}));
    assertEquals(List.of(YES, NO), network.recall(new int[] {1, 0}));
    assertEquals(List.of(YES, YES), network.recall(new int[] {1, 1}));
  }

  @Test
  void teachesNotThroughEightLayers() {
    final List<CyberNeuron> layers = new ArrayList<>();
    layers.add(this.cleared(CyberNeuron.of(1, 1)));
    for (int layer = 1; layer < 8; layer++) {
      layers.add(this.cleared(CyberNeuron.of(1, YES.ordinal())));
    }
    CyberNetwork.Builder builder = CyberNetwork.externalInputs(1).bind(layers.get(0), 0, 0);
    for (int layer = 1; layer < layers.size(); layer++) {
      builder = builder.connect(layers.get(layer - 1), layers.get(layer), 0);
    }
    final CyberNetwork network = builder.output(layers.get(7)).build();

    for (int pass = 0; pass < 200; pass++) {
      network.teach(new int[] {0}, List.of(YES), SEQUENTIAL, 1);
      network.teach(new int[] {1}, List.of(NO), SEQUENTIAL, 1);
    }

    assertEquals(List.of(YES), network.recall(new int[] {0}));
    assertEquals(List.of(NO), network.recall(new int[] {1}));
  }

  @Test
  void teachesDoorAccessRule() {
    final CyberNeuron badge = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron afterHours = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron lockReleased = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron guardOnDuty = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron grant = this.cleared(CyberNeuron.of(4, YES.ordinal()));
    final CyberNetwork network = CyberNetwork.externalInputs(4)
        .bind(badge, 0, 0)
        .bind(afterHours, 0, 1)
        .bind(lockReleased, 0, 2)
        .bind(guardOnDuty, 0, 3)
        .connect(badge, grant, 0)
        .connect(afterHours, grant, 1)
        .connect(lockReleased, grant, 2)
        .connect(guardOnDuty, grant, 3)
        .output(grant)
        .build();

    System.out.println(network.makeDotDiagram());

    final List<int[]> samples = this.doorSamples();
    for (int pass = 0; pass < 160; pass++) {
      for (final int[] sample : samples) {
        network.teach(sample, List.of(this.doorOpens(sample) ? YES : NO), SEQUENTIAL, 1);
      }
    }

    for (final int[] sample : samples) {
      assertEquals(List.of(this.doorOpens(sample) ? YES : NO), network.recall(sample));
    }
  }

  @Test
  void diagramsTwoHiddenLayers() {
    final CyberNeuron left = CyberNeuron.of(1, 1);
    final CyberNeuron right = CyberNeuron.of(1, 1);
    final CyberNeuron upper = CyberNeuron.of(2, YES.ordinal());
    final CyberNeuron lower = CyberNeuron.of(2, YES.ordinal());
    final CyberNeuron decision = CyberNeuron.of(2, YES.ordinal());
    final String dot = CyberNetwork.externalInputs(2)
        .bind(left, 0, 0)
        .bind(right, 0, 1)
        .connect(left, upper, 0)
        .connect(right, upper, 1)
        .connect(left, lower, 0)
        .connect(right, lower, 1)
        .connect(upper, decision, 0)
        .connect(lower, decision, 1)
        .output(decision)
        .build()
        .makeDotDiagram();

    System.out.println(dot);

    assertTrue(dot.startsWith("digraph G {"));
    assertEquals(2L, dot.lines().filter(line -> line.contains("[color=red;shape=box];")).count());
    assertEquals(4L,
        dot.lines().filter(line -> line.contains("[color=green;shape=oval];")).count());
    assertEquals(1L, dot.lines().filter(line -> line.contains("[color=green;shape=box];")).count());
    assertEquals(8L, dot.lines().filter(line -> line.contains("->")).count());
    assertEquals(6L, dot.lines().filter(line -> line.contains("headlabel=")).count());
  }

  @Test
  void diagramsExternalInputsHiddenNeuronsAndOutputs() {
    final CyberNeuron hidden = CyberNeuron.of(1, 1);
    final CyberNeuron decision = CyberNeuron.of(2, YES.ordinal());
    final String dot = CyberNetwork.externalInputs(2)
        .bind(hidden, 0, 0)
        .bind(decision, 0, 1)
        .connect(hidden, decision, 1)
        .output(decision)
        .build()
        .makeDotDiagram();

    assertTrue(dot.startsWith("digraph G {"));
    assertTrue(dot.contains("\"I0\" [color=red;shape=box];"));
    assertTrue(dot.contains("\"I1\" [color=red;shape=box];"));
    assertTrue(dot.contains("\"N0\" [color=green;shape=oval];"));
    assertTrue(dot.contains("\"N1\" [color=green;shape=box];"));
    assertTrue(dot.contains("\"I0\" -> \"N0\";"));
    assertTrue(dot.contains("\"I1\" -> \"N1\" [fontsize=8;headlabel=\"0\"];"));
    assertTrue(dot.contains("\"N0\" -> \"N1\" [fontsize=8;headlabel=\"1\"];"));
  }

  @Test
  void rejectsASignalReaderWithTooSmallAlphabet() {
    final CyberNeuron source = CyberNeuron.of(1, 1);
    final CyberNeuron target = CyberNeuron.of(1, 1);

    assertThrows(IllegalArgumentException.class, () -> CyberNetwork.externalInputs(1)
        .bind(source, 0, 0)
        .connect(source, target, 0));
  }

  @Test
  void teachesThreeInputOrFromZeroedTables() {
    final CyberNeuron first = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron second = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron third = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron orGate = this.cleared(CyberNeuron.of(3, YES.ordinal()));
    final CyberNetwork network = CyberNetwork.externalInputs(3)
        .bind(first, 0, 0)
        .bind(second, 0, 1)
        .bind(third, 0, 2)
        .connect(first, orGate, 0)
        .connect(second, orGate, 1)
        .connect(third, orGate, 2)
        .output(orGate)
        .build();

    for (int pass = 0; pass < 80; pass++) {
      for (int mask = 0; mask < 8; mask++) {
        final int[] sample = {(mask >> 2) & 1, (mask >> 1) & 1, mask & 1};
        network.teach(sample, List.of(mask == 0 ? NO : YES), RANDOM, 1);
      }
    }

    for (int mask = 0; mask < 8; mask++) {
      final int[] sample = {(mask >> 2) & 1, (mask >> 1) & 1, mask & 1};
      assertEquals(List.of(mask == 0 ? NO : YES), network.recall(sample));
    }
  }

  @Test
  void teachesTwoOfThreeMajorityVote() {
    final CyberNeuron first = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron second = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron third = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron vote = this.cleared(CyberNeuron.of(3, YES.ordinal()));
    final CyberNetwork network = CyberNetwork.externalInputs(3)
        .bind(first, 0, 0)
        .bind(second, 0, 1)
        .bind(third, 0, 2)
        .connect(first, vote, 0)
        .connect(second, vote, 1)
        .connect(third, vote, 2)
        .output(vote)
        .build();

    for (int pass = 0; pass < 100; pass++) {
      for (int mask = 0; mask < 8; mask++) {
        final int[] sample = {(mask >> 2) & 1, (mask >> 1) & 1, mask & 1};
        final boolean majority = Integer.bitCount(mask) >= 2;
        network.teach(sample, List.of(majority ? YES : NO), SEQUENTIAL, 1);
      }
    }

    for (int mask = 0; mask < 8; mask++) {
      final int[] sample = {(mask >> 2) & 1, (mask >> 1) & 1, mask & 1};
      final boolean majority = Integer.bitCount(mask) >= 2;
      assertEquals(List.of(majority ? YES : NO), network.recall(sample));
    }
  }

  @Test
  void teachesSharedHiddenDetectorsForSimultaneousOrAndOutputs() {
    final CyberNeuron sharedLeft = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron sharedRight = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron orGate = this.cleared(CyberNeuron.of(2, YES.ordinal()));
    final CyberNeuron andGate = this.cleared(CyberNeuron.of(2, YES.ordinal()));
    final CyberNetwork network = CyberNetwork.externalInputs(2)
        .bind(sharedLeft, 0, 0)
        .bind(sharedRight, 0, 1)
        .connect(sharedLeft, orGate, 0)
        .connect(sharedRight, orGate, 1)
        .connect(sharedLeft, andGate, 0)
        .connect(sharedRight, andGate, 1)
        .output(orGate)
        .output(andGate)
        .build();

    final int[][] samples = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
    final List<List<ConfidenceDegree>> labels = List.of(
        List.of(NO, NO),
        List.of(YES, NO),
        List.of(YES, NO),
        List.of(YES, YES));
    for (int pass = 0; pass < 80; pass++) {
      for (int i = 0; i < samples.length; i++) {
        network.teach(samples[i], labels.get(i), SEQUENTIAL, 1);
      }
    }

    for (int i = 0; i < samples.length; i++) {
      assertEquals(labels.get(i), network.recall(samples[i]));
    }
  }

  @Test
  void teachesXorThroughTwoHiddenNeurons() {
    final CyberNeuron upper = CyberNeuron.of(2, 1, new Random(11L));
    final CyberNeuron lower = CyberNeuron.of(2, 1, new Random(29L));
    final CyberNeuron xorGate = CyberNeuron.of(2, YES.ordinal(), new Random(47L));
    final CyberNetwork network = CyberNetwork.externalInputs(2)
        .bind(upper, 0, 0)
        .bind(upper, 1, 1)
        .bind(lower, 0, 0)
        .bind(lower, 1, 1)
        .connect(upper, xorGate, 0)
        .connect(lower, xorGate, 1)
        .output(xorGate)
        .build();

    final int[][] samples = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
    final List<ConfidenceDegree> expected = List.of(NO, YES, YES, NO);
    for (int pass = 0; pass < 120; pass++) {
      for (int i = 0; i < samples.length; i++) {
        network.teach(samples[i], List.of(expected.get(i)), SEQUENTIAL, 1);
      }
    }

    for (int i = 0; i < samples.length; i++) {
      assertEquals(List.of(expected.get(i)), network.recall(samples[i]));
    }
  }

  @Test
  void teachesOrOfAndsWithHiddenPairDetectors() {
    final CyberNeuron firstPair = this.cleared(CyberNeuron.of(2, 1));
    final CyberNeuron secondPair = this.cleared(CyberNeuron.of(2, 1));
    final CyberNeuron orGate = this.cleared(CyberNeuron.of(2, YES.ordinal()));
    final CyberNetwork network = CyberNetwork.externalInputs(4)
        .bind(firstPair, 0, 0)
        .bind(firstPair, 1, 1)
        .bind(secondPair, 0, 2)
        .bind(secondPair, 1, 3)
        .connect(firstPair, orGate, 0)
        .connect(secondPair, orGate, 1)
        .output(orGate)
        .build();

    for (int pass = 0; pass < 120; pass++) {
      for (int mask = 0; mask < 16; mask++) {
        final int[] sample = {mask & 1, (mask >> 1) & 1, (mask >> 2) & 1, (mask >> 3) & 1};
        final boolean matches = (mask & 3) == 3 || (mask & 12) == 12;
        network.teach(sample, List.of(matches ? YES : NO), SEQUENTIAL, 1);
      }
    }

    for (int mask = 0; mask < 16; mask++) {
      final int[] sample = {mask & 1, (mask >> 1) & 1, (mask >> 2) & 1, (mask >> 3) & 1};
      final boolean matches = (mask & 3) == 3 || (mask & 12) == 12;
      assertEquals(List.of(matches ? YES : NO), network.recall(sample));
    }
  }

  @Test
  void teachesMultiLayerNetworkWithLearningDividerFour() {
    final CyberNeuron left = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron right = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron andGate = this.cleared(CyberNeuron.of(2, YES.ordinal()));
    final CyberNetwork network = CyberNetwork.externalInputs(2)
        .bind(left, 0, 0)
        .bind(right, 0, 1)
        .connect(left, andGate, 0)
        .connect(right, andGate, 1)
        .output(andGate)
        .build();

    final int[][] samples = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
    for (int pass = 0; pass < 160; pass++) {
      for (final int[] sample : samples) {
        final boolean both = sample[0] == 1 && sample[1] == 1;
        network.teach(sample, List.of(both ? YES : NO), RANDOM, 4);
      }
    }

    assertEquals(List.of(NO), network.recall(new int[] {0, 0}));
    assertEquals(List.of(NO), network.recall(new int[] {0, 1}));
    assertEquals(List.of(NO), network.recall(new int[] {1, 0}));
    assertEquals(List.of(YES), network.recall(new int[] {1, 1}));
  }

  @Test
  void teachesMultiSlotConnectionFromSameSourceToSameConsumer() {
    final CyberNeuron hidden = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron decision = this.cleared(CyberNeuron.of(2, YES.ordinal()));
    final CyberNetwork network = CyberNetwork.externalInputs(1)
        .bind(hidden, 0, 0)
        .connect(hidden, decision, 0)
        .connect(hidden, decision, 1)
        .output(decision)
        .build();

    for (int pass = 0; pass < 40; pass++) {
      network.teach(new int[] {0}, List.of(NO), SEQUENTIAL, 1);
      network.teach(new int[] {1}, List.of(YES), SEQUENTIAL, 1);
    }

    assertEquals(List.of(NO), network.recall(new int[] {0}));
    assertEquals(List.of(YES), network.recall(new int[] {1}));
  }

  @Test
  void rejectsCyclesUnboundSlotsAndLockedNeuronWithoutPartialUpdate() {
    final CyberNeuron first = this.cleared(CyberNeuron.of(1, YES.ordinal()));
    final CyberNeuron second = this.cleared(CyberNeuron.of(1, YES.ordinal()));
    assertThrows(IllegalStateException.class, () -> CyberNetwork.externalInputs(1)
        .connect(first, second, 0)
        .connect(second, first, 0)
        .output(second)
        .build());

    final CyberNeuron incomplete = CyberNeuron.of(2, 1);
    assertThrows(IllegalStateException.class, () -> CyberNetwork.externalInputs(2)
        .bind(incomplete, 0, 0)
        .output(incomplete)
        .build());

    final CyberNeuron hidden = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron output = this.cleared(CyberNeuron.of(1, YES.ordinal()));
    final CyberNetwork network = CyberNetwork.externalInputs(1)
        .bind(hidden, 0, 0)
        .connect(hidden, output, 0)
        .output(output)
        .build();
    output.setLock(true);

    assertThrows(IllegalStateException.class,
        () -> network.teach(new int[] {0}, List.of(YES), SEQUENTIAL, 1));
    assertEquals(0, hidden.getTableValue(0));
  }

  private List<int[]> doorSamples() {
    final List<int[]> samples = new ArrayList<>();
    for (int mask = 0; mask < 16; mask++) {
      samples.add(new int[] {(mask >> 3) & 1, (mask >> 2) & 1, (mask >> 1) & 1, mask & 1});
    }
    return samples;
  }

  private boolean doorOpens(final int[] sample) {
    return sample[0] == 1 && sample[1] == 0 && sample[2] == 1 && sample[3] == 1;
  }

  private int hiddenCellAfter(final Integer weight) {
    final CyberNeuron hidden = this.cleared(CyberNeuron.of(1, 1));
    final CyberNeuron decision = this.cleared(CyberNeuron.of(1, YES.ordinal()));
    CyberNetwork.Builder builder = CyberNetwork.externalInputs(1);
    if (weight != null) {
      builder = builder.layer(weight, hidden);
    }
    builder.bind(hidden, 0, 0).connect(hidden, decision, 0).output(decision).build()
        .teach(new int[] {0}, List.of(YES), SEQUENTIAL, 12);
    return hidden.getTableValue(0);
  }

  private CyberNeuron cleared(final CyberNeuron neuron) {
    neuron.fill(new byte[neuron.getInputSize() * neuron.getRowLength()]);
    return neuron;
  }
}
