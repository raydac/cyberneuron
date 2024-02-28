package com.igormaznitsa.cyberneuro.core;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static java.util.stream.Stream.concat;

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

public class CyberNet implements CyberNetEntity, HasInput, HasOutput {
  private final Map<CyberNetEntity, Set<CyberLink>> internalEntities;
  private final List<CyberNetInputPin> inputs;
  private final List<CyberNetOutputPin> outputs;

  private final long uid;

  public CyberNet() {
    this.uid = UID_GENERATOR.incrementAndGet();
    this.internalEntities = new HashMap<>();
    this.inputs = new ArrayList<>();
    this.outputs = new ArrayList<>();
  }

  @Override
  public boolean isValidInternally() {
    return this.findErrors().isEmpty();
  }

  public void addNeuron(final CyberNeuron neuron) {
    if (this.internalEntities.containsKey(requireNonNull(neuron))) {
      throw new IllegalStateException("Neuron already presented in the network");
    }
    this.internalEntities.put(neuron, new HashSet<>());
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

  public CyberNetOutputPin addOutputPin() {
    final CyberNetOutputPin newOutput = CyberNetOutputPin.makeNew();
    this.outputs.add(newOutput);
    return newOutput;
  }

  public CyberNetInputPin addInputPin() {
    final CyberNetInputPin newInput = CyberNetInputPin.makeNew();
    this.inputs.add(newInput);
    return newInput;
  }

  private boolean contains(final CyberNetEntity entity) {
    if (entity instanceof CyberNetInputPin) {
      return this.inputs.contains(entity);
    }

    if (entity instanceof CyberNetOutputPin) {
      return this.outputs.contains(entity);
    }

    return this.internalEntities.containsKey(entity);
  }

  public CyberLink link(
      final CyberNetEntity src,
      final CyberNetEntity target,
      final int targetIndex
  ) {
    if (!this.contains(src) && !this.contains(target)) {
      throw new NoSuchElementException("Can't find entity among registered ones");
    }

    if (this.hasLink(target, targetIndex)) {
      throw new IllegalStateException("Target input already busy");
    }

    if (!(src instanceof HasOutput)) {
      throw new IllegalStateException("Source must have output feature");
    }

    if (!(target instanceof HasInput)) {
      throw new IllegalStateException("Source must have input feature");
    }

    var newLink = new CyberLink((HasOutput) src, (HasInput) target, targetIndex);
    this.internalEntities.computeIfAbsent(src, x -> new HashSet<>()).add(newLink);
    return newLink;
  }

  @Override
  public CyberNetEntity makeCopy() {
    final CyberNet copyToReturn = new CyberNet();

    final Map<Long, CyberNetEntity> mapOldIdToCopy = new HashMap<>();
    this.inputs.forEach(x -> {
      var newInput = x.makeCopy();
      copyToReturn.inputs.add((CyberNetInputPin) newInput);
      mapOldIdToCopy.put(x.getUid(), newInput);
    });

    this.outputs.forEach(x -> {
      var newOutput = x.makeCopy();
      copyToReturn.outputs.add((CyberNetOutputPin) newOutput);
      mapOldIdToCopy.put(x.getUid(), newOutput);
    });

    this.internalEntities.keySet()
        .forEach(x -> {
          final CyberNetEntity copied = x.makeCopy();
          mapOldIdToCopy.put(x.getUid(), copied);
          copyToReturn.internalEntities.put(copied, new HashSet<>());
        });

    this.internalEntities.forEach((key, value) -> {
      final CyberNetEntity newEntity = requireNonNull(mapOldIdToCopy.get(key.getUid()));
      for (final CyberLink link : value) {
        final CyberNetEntity newTarget = requireNonNull(mapOldIdToCopy.get(link.target().getUid()));
        copyToReturn.link(newEntity, newTarget, link.targetInputIndex());
      }
    });

    return copyToReturn;
  }

  public Set<CyberNetEntity> findErrors() {
    var findErrorInputs = this.inputs.stream()
        .filter(x -> {
          var links = this.internalEntities.get(x);
          return links == null || links.isEmpty();
        });

    var findErrorOutputs = this.outputs.stream()
        .filter(x -> {
          var foundSources = this.internalEntities.entrySet().stream()
              .flatMap(z -> z.getValue().stream())
              .filter(z -> x.equals(z.target()))
              .toList();
          return foundSources.size() != 1;
        });

    var findErrorInternalEntities = this.internalEntities.keySet().stream()
        .filter(entity -> {
          final Map<Integer, Integer> inputUse = new HashMap<>();
          var outputUse = new AtomicInteger(0);
          this.internalEntities.entrySet().stream()
              .flatMap(z -> z.getValue().stream())
              .filter(z -> entity.equals(z.target()) || entity.equals(z.source()))
              .forEach(z -> {
                if (entity.equals(z.target())) {
                  inputUse.merge(z.targetInputIndex(), 1, Integer::sum);
                }
                if (entity.equals(z.source())) {
                  outputUse.incrementAndGet();
                }
              });
          return outputUse.get() == 0 ||
              entity instanceof HasInput hasInput && inputUse.size() < hasInput.getInputSize();
        });

    var findInternallyInvalid = this.internalEntities.keySet().stream()
        .filter(x -> !x.isValidInternally());

    return concat(findErrorInputs,
        concat(findInternallyInvalid,
            concat(
                findErrorOutputs,
                findErrorInternalEntities
            ))).collect(
        toUnmodifiableSet());
  }

  public boolean isValid() {
    return this.findErrors().isEmpty();
  }

  public List<CyberNetInputPin> findInputs() {
    return List.copyOf(this.inputs);
  }

  public List<CyberNetOutputPin> findOutputs() {
    return List.copyOf(this.outputs);
  }

  public Set<CyberNeuron> findNeurons() {
    return this.internalEntities.keySet().stream()
        .filter(CyberNeuron.class::isInstance)
        .map(x -> (CyberNeuron) x)
        .collect(Collectors.toSet());
  }

  public boolean hasLink(final CyberNetEntity entity, final int inputIndex) {
    if (entity instanceof CyberNeuron) {
      return this.internalEntities.values().stream()
          .flatMap(Collection::stream)
          .anyMatch(x -> x.target().equals(entity)
              && x.targetInputIndex() == inputIndex);
    } else if (entity instanceof CyberNetOutputPin) {
      return this.internalEntities.values().stream().flatMap(Collection::stream)
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

    this.internalEntities.entrySet().stream()
        .filter(e -> !processedEntities.containsKey(e.getKey()))
        .filter(e -> e.getKey() instanceof CyberNeuron)
        .forEach(e -> processedEntities.computeIfAbsent(e.getKey(), k -> {
          final String id = makeReadableId(k);
              builder.append('\"').append(id).append("\" [color=blue;shape=oval];").append(eol);
              return id;
            })
        );

    this.internalEntities.values().stream().flatMap(Collection::stream)
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
