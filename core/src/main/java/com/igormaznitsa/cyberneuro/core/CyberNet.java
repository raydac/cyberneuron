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

public class CyberNet implements HasCyberNetIn, HasCyberNetOut {
  private final Map<CyberNetEntity, Set<CyberLink>> entities;
  private final List<CyberNetInputPin> inputs;
  private final List<CyberNetOutputPin> outputs;

  private final long uid;

  public CyberNet() {
    this.uid = UID_GENERATOR.incrementAndGet();
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

  private static String makeReadableId(final CyberNetEntity entity) {
    if (entity instanceof CyberNeuron) {
      return "N_" + entity.getUid();
    }
    if (entity instanceof CyberNetOutputPin) {
      return "O_" + entity.getUid();
    }
    if (entity instanceof CyberNetInputPin) {
      return "I_" + entity.getUid();
    }
    if (entity instanceof CyberNet) {
      return "M_" + entity.getUid();
    }
    throw new IllegalArgumentException("Unexpected type: " + entity);
  }

  public CyberNetOutputPin addOutput() {
    final CyberNetOutputPin newOutput = CyberNetOutputPin.makeNew();
    this.outputs.add(newOutput);
    return newOutput;
  }

  public CyberNetInputPin addInput() {
    final CyberNetInputPin newInput = CyberNetInputPin.makeNew();
    this.inputs.add(newInput);
    return newInput;
  }

  public void freeLinkedInput(final CyberNeuron neuron, final int inputIndex) {
    this.entities.values()
        .forEach(
            x -> x.removeIf(y -> y.targetInputIndex() == inputIndex && y.target().equals(neuron)));
  }

  private boolean hasEntity(final CyberNetEntity entity) {
    return this.entities.containsKey(entity);
  }

  public CyberLink makeLink(
      final HasCyberNetOut src,
      final HasCyberNetIn target,
      final int targetIndex
  ) {
    if (!this.hasEntity(src)) {
      throw new NoSuchElementException("Can't find source entity among registered neurons");
    }

    if (!this.hasEntity(target)) {
      throw new NoSuchElementException("Can't find target entity among registered neurons");
    }

    if (this.isLinked(target, targetIndex)) {
      throw new IllegalStateException("Target input already linked");
    }

    var newLink = new CyberLink(src, target, targetIndex);
    this.entities.computeIfAbsent(src, x -> new HashSet<>()).add(newLink);
    return newLink;
  }

  public void remove(final CyberNetEntity entity) {
    if (this.entities.remove(entity) != null) {
      if (entity instanceof CyberNetInputPin) {
        this.inputs.remove(entity);
      }
      if (entity instanceof CyberNetOutputPin) {
        this.outputs.remove(entity);
      }
    }
  }

  public List<CyberNetInputPin> findErrorInputs() {
    return this.inputs.stream()
        .filter(x -> {
          var links = this.entities.get(x);
          return links == null || links.isEmpty();
        }).toList();
  }

  public List<CyberNetOutputPin> findErrorOutputs() {
    return this.outputs.stream()
        .filter(x -> {
          var foundSources = this.entities.entrySet().stream()
              .flatMap(z -> z.getValue().stream())
              .filter(z -> x.equals(z.target()))
              .toList();
          return foundSources.size() != 1;
        }).toList();
  }

  public List<CyberNeuron> findErrorNeurons() {
    return this.entities.entrySet().stream()
        .filter(x -> x.getKey() instanceof CyberNeuron)
        .map(x -> (CyberNeuron) x.getKey())
        .filter(neuron -> {
          final Map<Integer, Integer> inputUse = new HashMap<>();
          var outputUse = new AtomicInteger(0);
          this.entities.entrySet().stream()
              .flatMap(z -> z.getValue().stream())
              .filter(z -> neuron.equals(z.target()) || neuron.equals(z.source()))
              .forEach(z -> {
                if (neuron.equals(z.target())) {
                  inputUse.merge(z.targetInputIndex(), 1, Integer::sum);
                }
                if (neuron.equals(z.source())) {
                  outputUse.incrementAndGet();
                }
              });
          return inputUse.size() < neuron.getInputSize() || outputUse.get() == 0;
        })
        .toList();
  }

  public boolean isValid() {
    if (!this.findErrorInputs().isEmpty()) {
      return false;
    }
    if (!this.findErrorOutputs().isEmpty()) {
      return false;
    }
    return findErrorNeurons().isEmpty();
  }

  public List<CyberNetInputPin> findInputs() {
    return List.copyOf(this.inputs);
  }

  public List<CyberNetOutputPin> findOutputs() {
    return List.copyOf(this.outputs);
  }

  public Set<CyberNeuron> findNeurons() {
    return this.entities.keySet().stream()
        .filter(CyberNeuron.class::isInstance)
        .map(x -> (CyberNeuron) x)
        .collect(Collectors.toSet());
  }

  public boolean isLinked(final CyberNetEntity entity, final int inputIndex) {
    if (entity instanceof CyberNeuron) {
      return this.entities.values().stream()
          .flatMap(Collection::stream)
          .anyMatch(x -> x.target().equals(entity)
              && x.targetInputIndex() == inputIndex);
    } else if (entity instanceof CyberNetOutputPin) {
      return this.entities.values().stream().flatMap(Collection::stream)
          .anyMatch(x -> x.target().equals(entity));
    } else {
      throw new IllegalArgumentException(
          "Not allowed input entity type: " + entity.getClass().getName());
    }
  }

  public String makeDotDiagram() {
    final StringBuilder builder = new StringBuilder();
    final String eol = "\n";

    builder.append("digraph G {").append(eol)
        .append("graph [splines=true]").append(eol)
        .append("rankdir=LR;").append(eol);

    final Map<CyberNetEntity, String> processedEntities = new HashMap<>();

    this.inputs.forEach(x -> processedEntities.computeIfAbsent(x, k -> {
      final String id = makeReadableId(k);
      builder.append('\"').append(id).append("\" [color=green;shape=box];").append(eol);
      return id;
    }));

    this.outputs.forEach(x -> processedEntities.computeIfAbsent(x, k -> {
      final String id = makeReadableId(k);
      builder.append('\"').append(id).append("\" [color=red;shape=box];").append(eol);
      return id;
    }));

    this.entities.entrySet().stream()
        .filter(e -> !processedEntities.containsKey(e.getKey()))
        .filter(e -> e.getKey() instanceof CyberNeuron)
        .forEach(e -> processedEntities.computeIfAbsent(e.getKey(), k -> {
          final String id = makeReadableId(k);
              builder.append('\"').append(id).append("\" [color=blue;shape=oval];").append(eol);
              return id;
            })
        );

    this.entities.values().stream().flatMap(Collection::stream)
        .forEach(l -> {
          final String idSource = processedEntities.get(l.source());
          final String idTarget = processedEntities.get(l.target());
          builder.append('\"').append(idSource).append("\" -> \"").append(idTarget)
              .append("\" [fontsize=8;label=\"").append(l.targetInputIndex()).append("\"];")
              .append(eol);
        });

    builder.append('}');

    return builder.toString();
  }

  @Override
  public long getUid() {
    return this.uid;
  }

  @Override
  public int getInputSize() {
    return this.inputs.size();
  }

  @Override
  public boolean isInputIndexValid(int index) {
    return index >= 0 && index < this.inputs.size();
  }

  @Override
  public int getOutputSize() {
    return this.outputs.size();
  }

  @Override
  public boolean isOutputIndexValid(final int index) {
    return index >= 0 && index < this.outputs.size();
  }
}
