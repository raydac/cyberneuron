package com.igormaznitsa.cyberneuro.core;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@SuppressWarnings({"UnusedReturnValue", "BooleanMethodIsAlwaysInverted"})
public class CyberNet implements CyberNetEntity, HasOutput {
  private final Map<CyberNetEntity, Set<CyberLink>> entities = new LinkedHashMap<>();
  private final long uid;
  private int inputCount;
  private int outputCount;

  public CyberNet() {
    this.uid = UID_GENERATOR.incrementAndGet();
  }

  private static String makeReadableId(final HasUid entity) {
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

  private static String findDotAttributesForEntity(final CyberNetEntity entity) {
    if (entity instanceof CyberNet) {
      return "[color=blue;shape=octagon]";
    } else if (entity instanceof CyberNeuron) {
      return "[color=green;shape=oval]";
    } else if (entity instanceof CyberNetInputPin) {
      return "[color=red;shape=box]";
    } else if (entity instanceof CyberNetOutputPin) {
      return "[color=green;shape=box]";
    } else {
      return "[color=gray;shape=triangle]";
    }
  }

  @Override
  public boolean hasInternalErrors() {
    return this.entities.entrySet()
        .stream()
        .anyMatch(e ->
            e.getKey().hasInternalErrors()
                || (!(e.getKey() instanceof CyberNetInputPin) &&
                this.findFirstFreeInputIndex(e.getKey()) >= 0)
                ||
                (e.getKey() instanceof HasOutput out && out.getOutputSize() > e.getValue().size())
        );
  }

  public void put(final CyberNetEntity entity) {
    if (this.entities.containsKey(entity)) {
      throw new IllegalStateException("Already presented in the network");
    }
    this.entities.put(entity, entity instanceof HasOutput ? new HashSet<>() : Set.of());
    if (entity instanceof CyberNetInputPin) {
      this.inputCount++;
    }
    if (entity instanceof CyberNetOutputPin) {
      this.outputCount++;
    }
  }

  public CyberNetOutputPin addOutput() {
    final CyberNetOutputPin newOutput = CyberNetOutputPin.makeNew();
    this.put(newOutput);
    return newOutput;
  }

  public CyberNetInputPin addInputPin() {
    final CyberNetInputPin newInput = CyberNetInputPin.makeNew();
    this.put(newInput);
    return newInput;
  }

  private boolean contains(final CyberNetEntity entity) {
    return this.entities.containsKey(entity);
  }

  public <S extends CyberNetEntity & HasOutput, T extends CyberNetEntity> CyberLink link(
      final S src,
      final int outputIndex,
      final T target,
      final int targetIndex
  ) {
    if (outputIndex < 0 || outputIndex >= src.getOutputSize()) {
      throw new IllegalArgumentException("Output index is wrong: " + outputIndex);
    }
    if (targetIndex < 0 || targetIndex >= target.getInputSize()) {
      throw new IllegalArgumentException("Input index is wrong: " + targetIndex);
    }

    if (this.entities.entrySet().stream().flatMap(x -> x.getValue().stream())
        .anyMatch(x -> x.target().equals(target) && x.targetIndex() == targetIndex)) {
      throw new IllegalStateException("Input with index " + targetIndex + " is already linked");
    }
    final CyberLink link = new CyberLink(src, outputIndex, target, targetIndex);
    this.entities.get(src).add(link);
    return link;
  }

  public <S extends CyberNetEntity & HasOutput, T extends CyberNetEntity> CyberLink link(
      final S src,
      final T target,
      final int targetIndex
  ) {
    return this.link(src, findFirstPreferableOutputIndex(src), target, targetIndex);
  }

  public <S extends CyberNetEntity & HasOutput, T extends CyberNetEntity> CyberLink link(
      final S src,
      final int outputIndex,
      final T target
  ) {
    return this.link(src, outputIndex, target, findFirstFreeInputIndex(target));
  }

  public <S extends CyberNetEntity & HasOutput, T extends CyberNetEntity> CyberLink link(
      final S src,
      final T target
  ) {
    return this.link(src, findFirstPreferableOutputIndex(src), target,
        findFirstFreeInputIndex(target));
  }

  public <A extends CyberNetEntity & HasOutput> int findFirstPreferableOutputIndex(
      final A a) {
    if (!this.entities.containsKey(a)) {
      throw new IllegalStateException("Argument is not among network entities");
    }
    if (a.getOutputSize() == 1) {
      return 0;
    }
    final Set<CyberLink> found = this.entities.get(a);
    return IntStream.range(0, a.getOutputSize())
        .filter(i -> found.stream().noneMatch(z -> z.sourceIndex() == i))
        .findFirst()
        .orElse(0);
  }

  public int findFirstFreeInputIndex(final CyberNetEntity input) {
    if (!this.entities.containsKey(input)) {
      throw new IllegalStateException("Argument is not among network entities");
    }
    final BitSet bitSet = new BitSet(input.getInputSize());
    this.entities.entrySet().stream()
        .flatMap(x -> x.getValue().stream())
        .filter(x -> x.target().equals(input))
        .forEach(x -> bitSet.set(x.targetIndex()));

    final int freeIndex = bitSet.nextClearBit(0);
    return freeIndex < input.getInputSize() ? freeIndex : -1;
  }

  @Override
  public CyberNetEntity makeCopy() {
    final CyberNet copyToReturn = new CyberNet();

    final Map<Long, CyberNetEntity> mapOldIdToCopy = new HashMap<>();

    this.entities.forEach((x, l) -> {
      var entityCopy = x.makeCopy();
      copyToReturn.put(entityCopy);
      mapOldIdToCopy.put(x.getUid(), entityCopy);
    });

    this.entities.keySet()
        .forEach(x -> {
          final CyberNetEntity copied = x.makeCopy();
          mapOldIdToCopy.put(x.getUid(), copied);
          copyToReturn.entities.put(copied, new HashSet<>());
        });

    this.entities.forEach((key, value) -> {
      final CyberNetEntity newEntity = requireNonNull(mapOldIdToCopy.get(key.getUid()));
      for (final CyberLink link : value) {
        final CyberNetEntity newTarget = requireNonNull(mapOldIdToCopy.get(link.target().getUid()));
        copyToReturn.link((CyberNetEntity & HasOutput) newEntity, link.sourceIndex(), newTarget,
            link.targetIndex());
      }
    });

    return copyToReturn;
  }

  public Set<CyberNetEntity> findErrors() {
    return this.entities.keySet().stream().filter(x -> !x.hasInternalErrors())
        .collect(toUnmodifiableSet());
  }

  public String makeDotDiagram() {
    final StringBuilder builder = new StringBuilder();
    final String eol = "\n";

    builder.append("digraph G {").append(eol)
        .append("graph [splines=true]").append(eol)
        .append("rankdir=LR;").append(eol);

    final Map<HasUid, String> processedEntities = new HashMap<>();

    this.entities.keySet().stream()
        .forEach(x -> {
          final String id = makeReadableId(x);
          final String attributes = findDotAttributesForEntity(x);
          builder.append('\"').append(id).append("\" ").append(attributes).append(';').append(eol);
        });

    this.entities.values().stream().flatMap(Collection::stream)
        .forEach(l -> {
          final String idSource = processedEntities.get(l.source());
          final String idTarget = processedEntities.get(l.target());
          builder.append('\"').append(idSource).append("\" -> \"").append(idTarget)
              .append("\" [fontsize=8;label=\"").append(l.targetIndex()).append("\"];")
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
  public int getOutputSize() {
    return this.outputCount;
  }

  @Override
  public boolean isOutputIndexValid(int index) {
    return index >= 0 && index < this.outputCount;
  }

  @Override
  public int getInputSize() {
    return this.inputCount;
  }

  @Override
  public boolean isInputIndexValid(int index) {
    return index >= 0 && index < this.inputCount;
  }

  public List<CyberLink> findIncomingLinks(final HasInput entity) {
    return this.entities.values().stream()
        .flatMap(Collection::stream)
        .filter(x -> x.target().equals(entity))
        .toList();
  }

  public List<CyberLink> findOutgoingLinks(final HasOutput entity) {
    return this.entities.values().stream()
        .flatMap(Collection::stream)
        .filter(x -> x.source().equals(entity))
        .toList();
  }

}
