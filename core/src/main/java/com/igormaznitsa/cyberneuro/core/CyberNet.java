package com.igormaznitsa.cyberneuro.core;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

@SuppressWarnings({"UnusedReturnValue", "BooleanMethodIsAlwaysInverted"})
public class CyberNet implements CyberNetEntity, HasOutput, HasLock, IsActivable {
  private final Map<CyberNetEntity, Set<CyberLink>> entities = new LinkedHashMap<>();
  private final long uid;
  private int inputCount;
  private int outputCount;

  private boolean lock;

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
  public boolean isLocked() {
    return this.lock;
  }

  @Override
  public void setLock(boolean flag) {
    this.lock = flag;
  }

  private static void addValueToResolved(
      final Map<ResultEntity, Integer> map,
      final ResultEntityCache resultEntityCache,
      final HasOutput hasOutput,
      final int[] outputs) {
    for (int p = 0; p < hasOutput.getOutputSize(); p++) {
      map.put(resultEntityCache.find(hasOutput, p), outputs[p]);
    }
  }

  public void put(final CyberNetEntity entity) {
    this.assertNonLocked();
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
    this.assertNonLocked();
    final CyberNetOutputPin newOutput = CyberNetOutputPin.makeNew();
    this.put(newOutput);
    return newOutput;
  }

  public CyberNetInputPin addInputPin() {
    this.assertNonLocked();
    final CyberNetInputPin newInput = CyberNetInputPin.makeNew();
    this.put(newInput);
    return newInput;
  }

  public <S extends CyberNetEntity & HasOutput, T extends CyberNetEntity> CyberLink link(
      final S src,
      final int outputIndex,
      final T target,
      final int targetIndex
  ) {
    this.assertNonLocked();
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

    copyToReturn.lock = this.lock;
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
          processedEntities.put(x, id);
        });

    this.entities.values().stream().flatMap(Collection::stream)
        .forEach(l -> {
          final String idSource = processedEntities.get(l.source());
          final String idTarget = processedEntities.get(l.target());
          builder.append('\"').append(idSource).append("\" -> \"").append(idTarget)
              .append("\" [fontsize=8")
              .append(l.target().getInputSize() > 1 ? ";headlabel=\"" + l.targetIndex() + '\"' : "")
              .append(
                  l.source().getOutputSize() > 1 ? ";taillabel=\"" + l.sourceIndex() + '\"' : "")
              .append("];")
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

  @Override
  public boolean hasInternalErrors() {
    return this.entities.entrySet()
        .stream()
        .anyMatch(e ->
            e.getKey().hasInternalErrors()
                || (!(e.getKey() instanceof CyberNetInputPin) &&
                this.findFirstFreeInputIndex(e.getKey()) >= 0)
                ||
                ((e.getKey() instanceof HasOutput out && !(out instanceof IsTerminator))
                    && out.getOutputSize() > e.getValue().size())
        );
  }

  public List<CyberLink> findOutgoingLinks(final HasOutput entity) {
    return this.entities.values().stream()
        .flatMap(Collection::stream)
        .filter(x -> x.source().equals(entity))
        .toList();
  }

  public List<CyberLink> findIncomingLinks(final HasInput entity) {
    return this.entities.values().stream()
        .flatMap(Collection::stream)
        .filter(x -> x.target().equals(entity))
        .sorted()
        .toList();
  }

  public List<List<CyberLink>> findWholeChain(final HasInput entity) {
    final List<List<CyberLink>> result = new ArrayList<>();
    List<CyberLink> found = this.findIncomingLinks(entity);

    while (!found.isEmpty()) {
      result.addFirst(found);
      List<CyberLink> newFound = new ArrayList<>();
      for (final CyberLink link : found) {
        if (link.source() instanceof HasInput input) {
          newFound.addAll(findIncomingLinks(input));
        }
      }
      found = newFound;
    }

    return result;
  }

  @Override
  public int[] activate(final int[] inputs) {
    if (inputs.length != this.inputCount) {
      throw new IllegalArgumentException(
          format("Wrong input length, detected %d but expected %d", inputs.length,
              this.inputCount));
    }

    final List<CyberNetOutputPin> allOutputs = this.entities.keySet().stream()
        .filter(
            CyberNetOutputPin.class::isInstance)
        .map(CyberNetOutputPin.class::cast)
        .toList();

    if (allOutputs.size() != this.outputCount) {
      throw new IllegalStateException(
          format("Can't find output pins, detected %d output pins but expected %d",
              allOutputs.size(),
              this.outputCount));
    }

    final List<CyberNetInputPin> allInputs = this.entities.keySet().stream()
        .filter(
            CyberNetInputPin.class::isInstance)
        .map(CyberNetInputPin.class::cast)
        .toList();

    if (allInputs.size() != this.inputCount) {
      throw new IllegalStateException(
          format("Can't find input pins, detected %d input pins but expected %d", allInputs.size(),
              this.inputCount));
    }

    final ResultEntityCache resultEntityCache = new ResultEntityCache();
    final Map<ResultEntity, Integer> resolved = new HashMap<>();

    for (int i = 0; i < inputs.length; i++) {
      addValueToResolved(
          resolved,
          resultEntityCache,
          allInputs.get(i),
          new int[] {inputs[i]}
      );
    }

    allOutputs.stream()
        .flatMap(x -> this.findWholeChain(x).stream().flatMap(Collection::stream))
        .forEach(link -> {
          if (link.target() instanceof IsActivable activable) {
            final int[] calculatedInputs = this.findIncomingLinks(link.target()).stream()
                .mapToInt(x -> Objects.requireNonNull(
                    resolved.get(resultEntityCache.find(x.source(), x.sourceIndex()))))
                .toArray();
            addValueToResolved(resolved, resultEntityCache, (HasOutput) activable,
                activable.activate(calculatedInputs));
          }
        });

    return allOutputs.stream().mapToInt(x -> resolved.get(resultEntityCache.find(x, 0))).toArray();
  }

  private record ResultEntity(HasOutput output, int outPinIndex) {
  }

  private static class ResultEntityCache {
    private final Map<HasOutput, Map<Integer, ResultEntity>> cache = new HashMap<>();

    public ResultEntity find(final HasOutput entity, final int index) {
      final Map<Integer, ResultEntity> indexMap =
          this.cache.computeIfAbsent(entity, f -> new HashMap<>());
      return indexMap.computeIfAbsent(index, i -> new ResultEntity(entity, i));
    }
  }

}
