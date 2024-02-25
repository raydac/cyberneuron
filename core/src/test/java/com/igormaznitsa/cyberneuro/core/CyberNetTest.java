package com.igormaznitsa.cyberneuro.core;

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

}