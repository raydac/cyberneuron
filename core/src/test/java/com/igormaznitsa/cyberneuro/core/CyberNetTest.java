package com.igormaznitsa.cyberneuro.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    var input1 = net.addInput();
    var input2 = net.addInput();
    var input3 = net.addInput();
    var out1 = net.addOutput();

    var neuron1 = CyberNeuron.of(3, 1);
    net.addNeuron(neuron1);

    net.addInternalLink(input1, neuron1, 0);
    net.addInternalLink(input2, neuron1, 1);
    net.addInternalLink(neuron1, out1, 0);

    assertFalse(net.isValid());
  }

  @Test
  void testNet_SingleNeuron() {
    CyberNet net = new CyberNet();
    var input1 = net.addInput();
    var input2 = net.addInput();
    var input3 = net.addInput();

    var neuron1 = CyberNeuron.of(3, 1);

    net.addNeuron(neuron1);

    var out1 = net.addOutput();

    net.addInternalLink(input1, neuron1, 0);
    net.addInternalLink(input2, neuron1, 1);
    net.addInternalLink(input3, neuron1, 2);

    net.addInternalLink(neuron1, out1, 0);

    assertTrue(net.isValid());

    logDiagram("Single neuron network", net);
  }

  @Test
  void testNet_3_2() {
    CyberNet net = new CyberNet();
    var input1 = net.addInput();
    var input2 = net.addInput();
    var input3 = net.addInput();

    var neuron1 = CyberNeuron.of(3, 1);
    var neuron2 = CyberNeuron.of(3, 1);

    var neuron3 = CyberNeuron.of(2, 1);
    var neuron4 = CyberNeuron.of(2, 1);

    net.addNeuron(neuron1);
    net.addNeuron(neuron2);
    net.addNeuron(neuron3);
    net.addNeuron(neuron4);

    var out1 = net.addOutput();
    var out2 = net.addOutput();

    net.addInternalLink(input1, neuron1, 0);
    net.addInternalLink(input2, neuron1, 1);
    net.addInternalLink(input3, neuron1, 2);

    net.addInternalLink(input1, neuron2, 0);
    net.addInternalLink(input2, neuron2, 1);
    net.addInternalLink(input3, neuron2, 2);

    net.addInternalLink(neuron1, neuron3, 0);
    net.addInternalLink(neuron1, neuron4, 1);

    net.addInternalLink(neuron2, neuron3, 0);
    net.addInternalLink(neuron2, neuron4, 1);

    net.addInternalLink(neuron3, out1, 0);
    net.addInternalLink(neuron4, out2, 0);

    assertFalse(net.isValid());

    logDiagram("3x2 network", net);
  }

}