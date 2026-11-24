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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Directed graph of neurons, input pins, and output pins.
 *
 * <p>{@link #activate(int[])} writes one sample symbol on each {@link CyberNetInputPin},
 * in insertion order, then walks links into the output pins. A neuron contributes
 * its raw sum. The next neuron treats that sum as a row index, so the sum has to
 * sit inside that row. Publish {@link CyberNeuron#signal(int[])} when the downstream
 * alphabet is the four confidence bands.
 *
 * <p>Each input slot accepts one link. The graph must be acyclic: a cycle makes
 * {@link #activate(int[])} and {@link #findWholeChain(HasInput)} run without end.
 * Not thread-safe. {@link #setLock(boolean)} rejects structural edits.
 */
@SuppressWarnings({"UnusedReturnValue", "BooleanMethodIsAlwaysInverted"})
public class CyberNet implements CyberNetEntity, HasOutput, HasLock, IsActivable {
  private final Map<CyberNetEntity, Set<CyberLink>> entities = new LinkedHashMap<>();
  private final long uid;
  private int inputCount;
  private int outputCount;

  private boolean lock;

  /**
   * Empty graph with a new id.
   */
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

  private static void addValueToResolved(
      final Map<ResultEntity, Integer> map,
      final ResultEntityCache resultEntityCache,
      final HasOutput hasOutput,
      final int[] outputs) {
    for (int p = 0; p < hasOutput.getOutputSize(); p++) {
      map.put(resultEntityCache.find(hasOutput, p), outputs[p]);
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

  /**
   * Adds a member that is not already in this graph.
   *
   * @param entity neuron, pin, or nested graph to own
   * @throws IllegalStateException when {@code entity} is already present or the graph is locked
   * @throws NullPointerException  when {@code entity} is null
   */
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

  /**
   * Adds a new sink pin and returns it.
   *
   * @return the pin, already a member of this graph
   * @throws IllegalStateException when the graph is locked
   */
  public CyberNetOutputPin addOutput() {
    this.assertNonLocked();
    final CyberNetOutputPin newOutput = CyberNetOutputPin.makeNew();
    this.put(newOutput);
    return newOutput;
  }

  /**
   * Adds a new source pin and returns it. Sample symbols follow this insertion order.
   *
   * @return the pin, already a member of this graph
   * @throws IllegalStateException when the graph is locked
   */
  public CyberNetInputPin addInputPin() {
    this.assertNonLocked();
    final CyberNetInputPin newInput = CyberNetInputPin.makeNew();
    this.put(newInput);
    return newInput;
  }

  /**
   * Links one output slot to one still-free input slot. Both ends must already be members.
   *
   * @param <S> producer type
   * @param <T> consumer type
   * @param src producer of the value
   * @param outputIndex output slot on {@code src}
   * @param target consumer of the value
   * @param targetIndex input slot on {@code target}; a second link to the same slot is rejected
   * @return the stored link
   * @throws IllegalArgumentException when a slot index is outside its element
   * @throws IllegalStateException when the graph is locked or {@code targetIndex} is already linked
   * @throws NullPointerException when {@code src} is not a member of this graph
   */
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

  /**
   * Links {@code targetIndex} from the first unused output slot of {@code src}.
   * A producer with one output always uses slot {@code 0}.
   *
   * @param <S> producer type
   * @param <T> consumer type
   * @param src producer of the value
   * @param target consumer of the value
   * @param targetIndex input slot on {@code target}
   * @return the stored link
   * @throws IllegalArgumentException when {@code targetIndex} is outside {@code target}
   * @throws IllegalStateException when the graph is locked, the slot is taken,
   *     or an end is not a member
   */
  public <S extends CyberNetEntity & HasOutput, T extends CyberNetEntity> CyberLink link(
      final S src,
      final T target,
      final int targetIndex
  ) {
    return this.link(src, findFirstPreferableOutputIndex(src), target, targetIndex);
  }

  /**
   * Links {@code outputIndex} to the first free input slot of {@code target}.
   *
   * @param <S> producer type
   * @param <T> consumer type
   * @param src producer of the value
   * @param outputIndex output slot on {@code src}
   * @param target consumer of the value
   * @return the stored link
   * @throws IllegalArgumentException when {@code outputIndex} is outside {@code src},
   *     or {@code target} has no free input slot
   * @throws IllegalStateException when the graph is locked or an end is not a member
   */
  public <S extends CyberNetEntity & HasOutput, T extends CyberNetEntity> CyberLink link(
      final S src,
      final int outputIndex,
      final T target
  ) {
    return this.link(src, outputIndex, target, findFirstFreeInputIndex(target));
  }

  /**
   * Links the first unused output of {@code src} to the first free input of {@code target}.
   *
   * @param <S> producer type
   * @param <T> consumer type
   * @param src producer of the value
   * @param target consumer of the value
   * @return the stored link
   * @throws IllegalArgumentException when {@code target} has no free input slot
   * @throws IllegalStateException when the graph is locked or an end is not a member
   */
  public <S extends CyberNetEntity & HasOutput, T extends CyberNetEntity> CyberLink link(
      final S src,
      final T target
  ) {
    return this.link(src, findFirstPreferableOutputIndex(src), target,
        findFirstFreeInputIndex(target));
  }

  /**
   * First output slot of {@code source} that does not yet start a link.
   * A one-output element returns {@code 0} even when that slot is already used.
   *
   * @param <A> producer type
   * @param source member of this graph
   * @return unused slot, or {@code 0} when every slot is already used
   * @throws IllegalStateException when {@code source} is not a member
   */
  public <A extends CyberNetEntity & HasOutput> int findFirstPreferableOutputIndex(
      final A source) {
    if (!this.entities.containsKey(source)) {
      throw new IllegalStateException("Argument is not among network entities");
    }
    if (source.getOutputSize() == 1) {
      return 0;
    }
    final Set<CyberLink> found = this.entities.get(source);
    return IntStream.range(0, source.getOutputSize())
        .filter(i -> found.stream().noneMatch(z -> z.sourceIndex() == i))
        .findFirst()
        .orElse(0);
  }

  /**
   * First input slot of {@code input} that no link targets yet.
   *
   * @param input member of this graph
   * @return free slot, or {@code -1} when every slot is taken
   * @throws IllegalStateException when {@code input} is not a member
   */
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

  /**
   * Another graph built from {@link CyberNetEntity#makeCopy()} of these members,
   * with links replayed onto the copies. The copy keeps this lock flag.
   *
   * @return a separate graph; member ids are new
   */
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

  /**
   * Members for which {@link CyberNetEntity#hasInternalErrors()} is false.
   * Unlinked slots of the whole graph are reported by {@link #hasInternalErrors()}.
   *
   * @return those members; empty when every member reports an internal error
   */
  public Set<CyberNetEntity> findErrors() {
    return this.entities.keySet().stream().filter(x -> !x.hasInternalErrors())
        .collect(toUnmodifiableSet());
  }

  /**
   * Graphviz {@code digraph} of members and links, laid out left to right.
   * Input pins are boxes, neurons are ovals, and multi-slot ends are labeled
   * with the slot index.
   *
   * @return a complete {@code digraph G} document
   */
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

  /**
   * Whether some member is unfinished.
   *
   * <p>True when a member other than an input pin still has a free input slot,
   * a non-terminator still has an output slot with no link, or a member's own
   * {@link CyberNetEntity#hasInternalErrors()} is true.
   *
   * @return {@code true} when the graph is not fully wired
   */
  @Override
  public boolean hasInternalErrors() {
    return this.entities.entrySet()
        .stream()
        .anyMatch(e ->
            e.getKey().hasInternalErrors()
                || (!(e.getKey() instanceof CyberNetInputPin) &&
                this.findFirstFreeInputIndex(e.getKey()) >= 0)
                ||
                (e.getKey() instanceof HasOutput && !(e.getKey() instanceof IsTerminator)
                    && ((HasOutput) e.getKey()).getOutputSize() > e.getValue().size())
        );
  }

  /**
   * Links that leave {@code entity}.
   *
   * @param entity producer to inspect; a non-member yields an empty list
   * @return those links, in encounter order
   */
  public List<CyberLink> findOutgoingLinks(final HasOutput entity) {
    return this.entities.values().stream()
        .flatMap(Collection::stream)
        .filter(x -> x.source().equals(entity))
        .collect(Collectors.toList());
  }

  /**
   * Links that enter {@code entity}, ordered by target slot.
   *
   * @param entity consumer to inspect; a non-member yields an empty list
   * @return those links, sorted by {@link CyberLink#targetIndex()}
   */
  public List<CyberLink> findIncomingLinks(final HasInput entity) {
    return this.entities.values().stream()
        .flatMap(Collection::stream)
        .filter(x -> x.target().equals(entity))
        .sorted()
        .collect(Collectors.toList());
  }

  /**
   * Incoming links of {@code entity}, then the incoming links of those sources,
   * and so on back to the pins. The first list is the layer nearest the sources.
   * Requires an acyclic graph.
   *
   * @param entity consumer whose ancestors are collected
   * @return layers from the sources toward {@code entity}; empty when nothing feeds it
   */
  public List<List<CyberLink>> findWholeChain(final HasInput entity) {
    final List<List<CyberLink>> result = new ArrayList<>();
    List<CyberLink> found = this.findIncomingLinks(entity);

    while (!found.isEmpty()) {
      result.add(0, found);
      List<CyberLink> newFound = new ArrayList<>();
      for (final CyberLink link : found) {
        if (link.source() instanceof HasInput) {
          newFound.addAll(findIncomingLinks((HasInput) link.source()));
        }
      }
      found = newFound;
    }

    return result;
  }

  /**
   * Pushes {@code inputs} through the graph. Index {@code i} is written to the
   * input pin added as number {@code i}. Each output pin contributes one result,
   * in the order the pins were added. Neuron-to-neuron values are raw sums.
   *
   * @param inputs one symbol per input pin
   * @return one value per output pin
   * @throws IllegalArgumentException when {@code inputs} length is not the pin count
   * @throws IllegalStateException when the stored pins do not match the pin counts
   * @throws NullPointerException when a consumer is evaluated before its producers
   */
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
        .collect(Collectors.toList());

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
        .collect(Collectors.toList());

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
          if (link.target() instanceof IsActivable) {
            final IsActivable activable = (IsActivable) link.target();
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

  private static class ResultEntityCache {
    private final Map<HasOutput, Map<Integer, ResultEntity>> cache = new HashMap<>();

    public ResultEntity find(final HasOutput entity, final int index) {
      final Map<Integer, ResultEntity> indexMap =
          this.cache.computeIfAbsent(entity, f -> new HashMap<>());
      return indexMap.computeIfAbsent(index, i -> new ResultEntity(entity, i));
    }
  }

  private static final class ResultEntity {
    private final HasOutput output;
    private final int outPinIndex;

    private ResultEntity(final HasOutput output, final int outPinIndex) {
      this.output = output;
      this.outPinIndex = outPinIndex;
    }

    public HasOutput output() {
      return output;
    }

    public int outPinIndex() {
      return outPinIndex;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj == null || obj.getClass() != this.getClass()) {
        return false;
      }
      var that = (ResultEntity) obj;
      return Objects.equals(this.output, that.output) &&
          this.outPinIndex == that.outPinIndex;
    }

    @Override
    public int hashCode() {
      return Objects.hash(output, outPinIndex);
    }

    @Override
    public String toString() {
      return "ResultEntity[" +
          "output=" + output + ", " +
          "outPinIndex=" + outPinIndex + ']';
    }

  }

}
