package com.igormaznitsa.cyberneuro.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CyberNetTest {

  private static void logDiagram(final String title, final CyberNet net) {
    System.out.println("-------------------------");
    System.out.println("  " + title);
    System.out.println("-------------------------");
    System.out.println(net.makeDotDiagram());
    System.out.println("-------------------------");
  }

  @Test
  void testNet_NonFullyConnectedNeuron() {
    CyberNet net = new CyberNet();
    var input1 = net.addInputPin();
    var input2 = net.addInputPin();
    var input3 = net.addInputPin();
    var out1 = net.addOutput();

    var neuron1 = CyberNeuron.of(3, 1);
    net.put(neuron1);

    net.link(input1, neuron1, 0);
    net.link(input2, neuron1, 1);
    net.link(neuron1, out1, 0);

    assertTrue(net.hasInternalErrors());
  }

  @Test
  void testErrorForDuplicationOfLinkToNeuronInput() {
    CyberNet net = new CyberNet();
    var input1 = net.addInputPin();
    var input2 = net.addInputPin();
    var neuron1 = CyberNeuron.of(2, 1);
    var out1 = net.addOutput();

    net.put(neuron1);

    net.link(input1, neuron1, 0);
    assertThrowsExactly(IllegalStateException.class,
        () -> net.link(input2, neuron1, 0));

    net.link(neuron1, out1, 0);
    assertThrowsExactly(IllegalStateException.class,
        () -> net.link(neuron1, out1, 0));
  }

  @Test
  void testNet_SingleNeuron() {
    CyberNet net = new CyberNet();
    var input1 = net.addInputPin();
    var input2 = net.addInputPin();
    var input3 = net.addInputPin();
    var input4 = net.addInputPin();
    var input5 = net.addInputPin();
    var input6 = net.addInputPin();

    final CyberNeuron neuron = CyberNeuron.of(6, 3);
    neuron.fill(new byte[] {
        0, 15, 0, 0,
        0, 0, 0, 20,
        14, 0, 0, 0,
        0, 18, 0, 0,
        0, 0, 16, 0,
        0, 17, 0, 0
    });

    net.put(neuron);

    var out1 = net.addOutput();

    net.link(input1, neuron, 0);
    net.link(input2, neuron, 1);
    net.link(input3, neuron, 2);
    net.link(input4, neuron, 3);
    net.link(input5, neuron, 4);
    net.link(input6, neuron, 5);

    net.link(neuron, out1, 0);

    assertFalse(net.hasInternalErrors());

    assertEquals(neuron.calc(new int[] {1, 3, 0, 1, 2, 1}),
        net.activate(new int[] {1, 3, 0, 1, 2, 1})[0]);

    logDiagram("Single neuron network", net);
  }

  @Test
  void testNet_3_2() {
    CyberNet net = new CyberNet();
    var input1 = net.addInputPin();
    var input2 = net.addInputPin();
    var input3 = net.addInputPin();

    var neuron1 = CyberNeuron.of(3, 1);
    var neuron2 = CyberNeuron.of(3, 1);

    var neuron3 = CyberNeuron.of(2, 1);
    var neuron4 = CyberNeuron.of(2, 1);

    net.put(neuron1);
    net.put(neuron2);
    net.put(neuron3);
    net.put(neuron4);

    var out1 = net.addOutput();
    var out2 = net.addOutput();

    net.link(input1, neuron1, 0);
    net.link(input2, neuron1, 1);
    net.link(input3, neuron1, 2);

    net.link(input1, neuron2, 0);
    net.link(input2, neuron2, 1);
    net.link(input3, neuron2, 2);

    net.link(neuron1, neuron3, 0);
    net.link(neuron1, neuron4, 0);

    net.link(neuron2, neuron3, 1);
    net.link(neuron2, neuron4, 1);

    net.link(neuron3, out1, 0);
    net.link(neuron4, out2, 0);

    assertFalse(net.hasInternalErrors());

    logDiagram("3x2 network", net);
  }

}