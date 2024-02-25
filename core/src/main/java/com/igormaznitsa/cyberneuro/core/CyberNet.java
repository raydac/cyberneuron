package com.igormaznitsa.cyberneuro.core;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CyberNet {
  private final Map<CyberNetEntity, Set<CyberLink>> entities;
  private final List<CyberNetInput> inputs;
  private final List<CyberNetOutput> outputs;

  public CyberNet() {
    this.entities = new HashMap<>();
    this.inputs = new ArrayList<>();
    this.outputs = new ArrayList<>();
  }

  public void addNeuron(final CyberNeuron neuron) {
    if (this.entities.containsKey(requireNonNull(neuron))) {
      throw new IllegalStateException("Neuron already presented in the network");
    }
    this.entities.put(neuron, new HashSet<>());
  }

  public CyberNetOutput addOutput() {
    final CyberNetOutput newOutput = CyberNetOutput.makeNew();
    this.outputs.add(newOutput);
    return newOutput;
  }

  public CyberNetInput addInput() {
    final CyberNetInput newInput = CyberNetInput.makeNew();
    this.inputs.add(newInput);
    return newInput;
  }

  private static String makeId(final CyberNetEntity entity) {
    if (entity instanceof CyberNeuron) {
      return "N_" + entity.getUid();
    }
    if (entity instanceof CyberNetOutput) {
      return "O_" + entity.getUid();
    }
    if (entity instanceof CyberNetInput) {
      return "I_" + entity.getUid();
    }
    throw new IllegalArgumentException("Unexpected type: " + entity);
  }

  public void freeLinkedInput(final CyberNeuron neuron, final int inputIndex) {
    this.entities.values()
        .forEach(
            x -> x.removeIf(y -> y.targetInputIndex() == inputIndex && y.target().equals(neuron)));
  }

  public CyberLink addInternalLink(final CyberNetEntity src, final CyberNetEntity target,
                                   final int targetIndex) {
    if (this.inputs.stream().noneMatch(x -> x.equals(src)) && !this.entities.containsKey(src)) {
      throw new NoSuchElementException("Can't find source entity among registered neurons");
    }

    if (this.outputs.stream().noneMatch(x -> x.equals(target)) &&
        !this.entities.containsKey(target)) {
      throw new NoSuchElementException("Can't find target entity among registered neurons");
    }

    if (this.hasLinkedInput(target, targetIndex)) {
      throw new IllegalStateException("Target input already linked");
    }

    var newLink = new CyberLink(src, target, targetIndex);
    this.entities.computeIfAbsent(src, x -> new HashSet<>()).add(newLink);
    return newLink;
  }

  public void remove(final CyberNetInput input) {
    this.inputs.remove(input);
    this.entities.remove(input);
  }

  public void remove(final CyberNetEntity entity) {
    if (this.entities.remove(entity) != null) {
      if (entity instanceof CyberNetInput) {
        this.inputs.remove(entity);
      }
      if (entity instanceof CyberNetOutput) {
        this.outputs.remove(entity);
      }
    }
  }

  public boolean isValid() {
    if (!(this.inputs.stream()
        .allMatch(x -> {
          var links = this.entities.get(x);
          return links.size() == 1;
        }) && this.outputs.stream()
        .allMatch(x -> this.entities.entrySet().stream()
            .flatMap(z -> z.getValue().stream())
            .anyMatch(y -> y.target().equals(x))))) {
      return false;
    }

    return this.entities.entrySet().stream()
        .filter(x -> x.getKey() instanceof CyberNeuron)
        .allMatch(x -> {
          var inputUse = new AtomicInteger(0);
          var outputUse = new AtomicInteger(0);
          this.entities.entrySet()
              .stream()
              .flatMap(z -> z.getValue().stream())
              .forEach(z -> {
                if (z.target().equals(x.getKey())) {
                  inputUse.incrementAndGet();
                }
                if (z.source().equals(x.getKey())) {
                  outputUse.incrementAndGet();
                }
              });
          var foundOutputs = outputUse.get();
          var foundInputs = inputUse.get();
          if (foundOutputs > 1 || foundInputs > ((CyberNeuron) x.getKey()).getInputSize()) {
            throw new IllegalStateException("Unexpected state of links for neuron");
          }
          return foundOutputs == 1 && foundInputs == ((CyberNeuron) x.getKey()).getInputSize();
        });
  }

  public List<CyberNetInput> findInputs() {
    return List.copyOf(this.inputs);
  }

  public List<CyberNetOutput> findOutputs() {
    return List.copyOf(this.outputs);
  }

  public Set<CyberNeuron> findNeurons() {
    return this.entities.keySet().stream()
        .filter(CyberNeuron.class::isInstance)
        .map(x -> (CyberNeuron) x)
        .collect(Collectors.toSet());
  }

  public boolean hasLinkedInput(final CyberNetEntity entity, final int inputIndex) {
    if (entity instanceof CyberNeuron) {
      return this.entities.values().stream()
          .flatMap(Collection::stream)
          .anyMatch(x -> x.source().equals(entity) && x.targetInputIndex() == inputIndex);
    } else if (entity instanceof CyberNetOutput) {
      return this.entities.values().stream().flatMap(Collection::stream)
          .anyMatch(x -> x.target().equals(entity));
    } else {
      throw new IllegalArgumentException("Allowed only type " + CyberNeuron.class.getSimpleName());
    }
  }

  public String makeDotDiagram() {
    final StringBuilder builder = new StringBuilder();
    final String eol = "\n";

    builder.append("digraph G {").append(eol)
        .append("rankdir=LR;").append(eol);

    final Map<CyberNetEntity, String> processedEntities = new HashMap<>();

    this.inputs.forEach(x -> processedEntities.computeIfAbsent(x, k -> {
      final String id = makeId(k);
      builder.append('\"').append(id).append("\" [color=green;shape=box];").append(eol);
      return id;
    }));

    this.outputs.forEach(x -> processedEntities.computeIfAbsent(x, k -> {
      final String id = makeId(k);
      builder.append('\"').append(id).append("\" [color=red;shape=box];").append(eol);
      return id;
    }));

    this.entities.entrySet().stream()
        .filter(e -> !processedEntities.containsKey(e.getKey()))
        .filter(e -> e.getKey() instanceof CyberNeuron)
        .forEach(e -> processedEntities.computeIfAbsent(e.getKey(), k -> {
              final String id = makeId(k);
              builder.append('\"').append(id).append("\" [color=blue;shape=oval];").append(eol);
              return id;
            })
        );

    this.entities.values().stream().flatMap(Collection::stream)
        .forEach(l -> {
          final String idSource = processedEntities.get(l.source());
          final String idTarget = processedEntities.get(l.target());
          builder.append('\"').append(idSource).append("\" -> \"").append(idTarget).append("\";")
              .append(eol);
        });

    builder.append('}');

    return builder.toString();
  }

}
