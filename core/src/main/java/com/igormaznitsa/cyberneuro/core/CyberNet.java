package com.igormaznitsa.cyberneuro.core;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class CyberNet {
  private final Map<CyberNeuron, Set<CyberLink>> neurons;

  public CyberNet() {
    this.neurons = new HashMap<>();
  }

  public void addNeuron(final CyberNeuron neuron) {
    if (this.neurons.containsKey(requireNonNull(neuron))) {
      throw new IllegalStateException("Neuron already presented in the network");
    }
    this.neurons.put(neuron, new HashSet<>());
  }

  public CyberLink addInputLink(final CyberNeuron src, final CyberNeuron target,
                                final int targetIndex) {
    if (!this.neurons.containsKey(src)) {
      throw new NoSuchElementException("Can't find source neurons among registered neurons");
    }

    if (!this.neurons.containsKey(target)) {
      throw new NoSuchElementException("Can't find target neurons among registered neurons");
    }

    if (this.hasLinkedInput(target, targetIndex)) {
      throw new IllegalStateException("Neuron input already linked");
    }

    var newLink = new CyberLink(src, target, targetIndex);
    this.neurons.computeIfAbsent(src, x -> new HashSet<>()).add(newLink);
    return newLink;
  }

  public void freeLinkedInput(final CyberNeuron neuron, final int inputIndex) {
    this.neurons.values()
        .forEach(
            x -> x.removeIf(y -> y.targetInputIndex() == inputIndex && y.target().equals(neuron)));
  }

  public boolean hasLinkedInput(final CyberNeuron neuron, final int inputIndex) {
    return this.neurons.values().stream()
        .flatMap(Collection::stream)
        .anyMatch(x -> x.source().equals(neuron) && x.targetInputIndex() == inputIndex);
  }

  public void remove(final CyberNeuron neuron) {
    this.neurons.remove(neuron);
  }

  public Set<CyberNeuron> neurons() {
    return this.neurons.keySet();
  }

}
