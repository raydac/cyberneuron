package com.igormaznitsa.cyberneuro.core;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Network assembled from existing {@link CyberNeuron}s.
 *
 * <p>Each input slot is bound once, either to an external symbol or to the
 * {@link CyberNeuron#signal(int[])} of an earlier neuron. {@link #recall(int[])}
 * runs that wiring. {@link #teach(int[], List, LearnStrategy, int)} measures the
 * signed gap of each output, then walks that error back through every neuron
 * that played a part. Each input of a consumer takes an equal share of the gap.
 * A hidden layer's weight divides the hop divider, so a heavier layer spends
 * more of that share. When another confidence selects a downstream cell that
 * reduces the gap, the neuron is taught toward that confidence. Hidden layers
 * use weight {@code 1} unless {@link Builder#layer(int, CyberNeuron...)} names
 * another positive weight. Declare only the terminal neurons as outputs.
 * Not thread-safe.
 */
public final class CyberNetwork {

  private static final int EQUAL_LAYER_WEIGHT = 1;

  private final int externalCount;
  private final List<CyberNeuron> order;
  private final List<CyberNeuron> outputs;
  private final Map<CyberNeuron, SlotSource[]> bindings;
  private final Map<CyberNeuron, List<Feed>> outgoing;
  private final Map<CyberNeuron, Integer> layerWeights;
  private final Map<CyberNeuron, Integer> depthFromOutput;

  private CyberNetwork(
      final int externalCount,
      final List<CyberNeuron> order,
      final List<CyberNeuron> outputs,
      final Map<CyberNeuron, SlotSource[]> bindings,
      final Map<CyberNeuron, List<Feed>> outgoing,
      final Map<CyberNeuron, Integer> layerWeights,
      final Map<CyberNeuron, Integer> depthFromOutput) {
    this.externalCount = externalCount;
    this.order = order;
    this.outputs = outputs;
    this.bindings = bindings;
    this.outgoing = outgoing;
    this.layerWeights = layerWeights;
    this.depthFromOutput = depthFromOutput;
  }

  /**
   * Starts a network whose external sample has {@code count} symbols.
   *
   * @param count external width; zero or positive
   * @return a builder with no neurons yet
   * @throws IllegalArgumentException when {@code count} is negative
   */
  public static Builder externalInputs(final int count) {
    if (count < 0) {
      throw new IllegalArgumentException("External input count must not be negative");
    }
    return new Builder(count);
  }

  /**
   * Confidence of each output neuron for this sample.
   *
   * @param external one symbol per external input, in index order
   * @return one band per output, in the order declared on the builder
   * @throws IllegalArgumentException when {@code external} has the wrong length
   *                                  or a symbol is outside the row that reads it
   */
  public List<ConfidenceDegree> recall(final int[] external) {
    final Forward forward = this.forward(external);
    final List<ConfidenceDegree> result = new ArrayList<>();
    for (final CyberNeuron output : this.outputs) {
      result.add(forward.bands.get(output));
    }
    return result;
  }

  /**
   * Graphviz {@code digraph} of external inputs, neurons, and bindings.
   * External inputs are red boxes. Hidden neurons are green ovals. Output
   * neurons are green boxes. A neuron with several slots labels each incoming
   * edge with the slot index. Neuron ids follow the forward order.
   *
   * @return a complete {@code digraph G} document
   */
  public String makeDotDiagram() {
    final String eol = "\n";
    final StringBuilder builder = new StringBuilder();
    final Map<CyberNeuron, String> ids = new LinkedHashMap<>();
    builder.append("digraph G {").append(eol);
    builder.append("graph [splines=true]").append(eol);
    builder.append("rankdir=LR;").append(eol);
    for (int index = 0; index < this.externalCount; index++) {
      builder.append("\"I").append(index).append("\" [color=red;shape=box];").append(eol);
    }
    for (int index = 0; index < this.order.size(); index++) {
      final CyberNeuron neuron = this.order.get(index);
      final String id = "N" + index;
      ids.put(neuron, id);
      final String shape = this.outputs.contains(neuron) ? "box" : "oval";
      builder.append('"').append(id).append("\" [color=green;shape=").append(shape).append("];")
          .append(eol);
    }
    for (final CyberNeuron neuron : this.order) {
      final SlotSource[] slots = this.bindings.get(neuron);
      for (int slot = 0; slot < slots.length; slot++) {
        final SlotSource source = slots[slot];
        final String from = source.neuron == null ? "I" + source.index : ids.get(source.neuron);
        builder.append('"').append(from).append("\" -> \"").append(ids.get(neuron)).append('"');
        if (slots.length > 1) {
          builder.append(" [fontsize=8;headlabel=\"").append(slot).append("\"]");
        }
        builder.append(';').append(eol);
      }
    }
    builder.append('}');
    return builder.toString();
  }

  private static int aligned(final int effect, final int gap) {
    if (effect == 0 || gap == 0 || Integer.signum(effect) != Integer.signum(gap)) {
      return effect == 0 || gap == 0 ? 0 : -Math.abs(effect);
    }
    return Math.min(Math.abs(effect), Math.abs(gap));
  }

  /**
   * Teaches the cells that fired while producing {@code expected}.
   * Each wrong output contributes its signed gap. That gap is pushed back
   * through every earlier neuron that played a part. Each of a consumer's
   * inputs takes an equal share of the gap. The layer weight divides the hop
   * divider, so a heavier layer spends more of that share. When another
   * confidence selects a downstream cell that reduces the gap, the neuron is
   * taught toward that confidence. Hidden layers use weight {@code 1} unless
   * {@link Builder#layer(int, CyberNeuron...)} sets another positive weight.
   *
   * @param external        one symbol per external input
   * @param expected        band of each output neuron, in output order
   * @param learnStrategy   how a neuron spreads its delta across the cells that fired
   * @param learningDivider positive divisor of each delta; each hop multiplies it
   * @throws IllegalArgumentException when a length, a symbol, or the divider is illegal
   * @throws IllegalStateException    when a neuron is locked
   */
  public void teach(
      final int[] external,
      final List<ConfidenceDegree> expected,
      final LearnStrategy learnStrategy,
      final int learningDivider) {
    requireNonNull(expected, "expected must not be null");
    requireNonNull(learnStrategy, "learnStrategy must not be null");
    if (learningDivider < 1) {
      throw new IllegalArgumentException("Learning divider must be positive");
    }
    if (expected.size() != this.outputs.size()) {
      throw new IllegalArgumentException(
          "Expected " + this.outputs.size() + " output bands but got " + expected.size());
    }

    final Forward forward = this.forward(external);
    final Map<CyberNeuron, Integer> gaps = new HashMap<>();
    final Map<CyberNeuron, ConfidenceDegree> targets = new HashMap<>();
    for (int index = this.order.size() - 1; index >= 0; index--) {
      this.assignGap(this.order.get(index), expected, forward, gaps, targets);
    }
    for (final CyberNeuron neuron : this.order) {
      final Integer gap = gaps.get(neuron);
      if (gap == null || gap == 0) {
        continue;
      }
      final int divider = this.dividerFor(neuron, learningDivider);
      final ConfidenceDegree target = targets.get(neuron);
      if (target != null) {
        neuron.teach(forward.inputs.get(neuron), learnStrategy, target, divider);
      } else {
        neuron.nudge(forward.inputs.get(neuron), learnStrategy, gap, divider);
      }
    }
  }

  private int dividerFor(final CyberNeuron neuron, final int learningDivider) {
    final Integer found = this.depthFromOutput.get(neuron);
    final int depth = found == null ? 0 : found;
    final int weight = this.outputs.contains(neuron)
        ? EQUAL_LAYER_WEIGHT
        : this.layerWeights.getOrDefault(neuron, EQUAL_LAYER_WEIGHT);
    final int hops = learningDivider * (depth + 1);
    return Math.max(1, hops / weight);
  }

  private void assignGap(
      final CyberNeuron neuron,
      final List<ConfidenceDegree> expected,
      final Forward forward,
      final Map<CyberNeuron, Integer> gaps,
      final Map<CyberNeuron, ConfidenceDegree> targets) {
    final int outputIndex = this.outputs.indexOf(neuron);
    if (outputIndex >= 0) {
      final int sum = forward.sums.get(neuron);
      final ConfidenceDegree wanted = expected.get(outputIndex);
      if (CyberNeuron.bandOf(sum) != wanted) {
        gaps.put(neuron, CyberNeuron.signedGap(sum, wanted));
        targets.put(neuron, wanted);
      }
      return;
    }

    final int currentSymbol = forward.bands.get(neuron).ordinal();
    final int usefulSymbol = this.usefulSymbol(neuron, currentSymbol, gaps);
    if (usefulSymbol != currentSymbol) {
      final ConfidenceDegree wanted = ConfidenceDegree.values()[usefulSymbol];
      final int sum = forward.sums.get(neuron);
      gaps.put(neuron, CyberNeuron.signedGap(sum, wanted));
      targets.put(neuron, wanted);
      return;
    }

    int share = 0;
    int signSource = 0;
    for (final Feed feed : this.outgoing.getOrDefault(neuron, List.of())) {
      final Integer downstream = gaps.get(feed.target);
      if (downstream == null || downstream == 0) {
        continue;
      }
      signSource = downstream;
      share += downstream / feed.target.getInputSize();
    }
    if (share == 0 && signSource != 0) {
      share = Integer.signum(signSource);
    }
    if (share != 0) {
      gaps.put(neuron, share);
    }
  }

  private int usefulSymbol(
      final CyberNeuron neuron,
      final int currentSymbol,
      final Map<CyberNeuron, Integer> gaps) {
    int bestSymbol = currentSymbol;
    int bestBenefit = 0;
    for (int symbol = 0; symbol < ConfidenceDegree.values().length; symbol++) {
      if (symbol == currentSymbol) {
        continue;
      }
      int benefit = 0;
      for (final Feed feed : this.outgoing.getOrDefault(neuron, List.of())) {
        final Integer downstream = gaps.get(feed.target);
        if (downstream == null || downstream == 0) {
          continue;
        }
        final int effect = this.cell(feed.target, feed.slot, symbol)
            - this.cell(feed.target, feed.slot, currentSymbol);
        benefit += aligned(effect, downstream);
      }
      if (benefit > bestBenefit) {
        bestBenefit = benefit;
        bestSymbol = symbol;
      }
    }
    return bestSymbol;
  }

  private int cell(final CyberNeuron neuron, final int slot, final int symbol) {
    return neuron.getTableValue(slot * neuron.getRowLength() + symbol);
  }

  private Forward forward(final int[] external) {
    if (external == null) {
      throw new IllegalArgumentException("External inputs must not be null");
    }
    if (external.length != this.externalCount) {
      throw new IllegalArgumentException(
          "Expected " + this.externalCount + " external inputs but got " + external.length);
    }
    final Forward forward = new Forward();
    final Map<CyberNeuron, Integer> signals = new HashMap<>();
    for (final CyberNeuron neuron : this.order) {
      final int[] inputs = new int[neuron.getInputSize()];
      final SlotSource[] slots = this.bindings.get(neuron);
      for (int slot = 0; slot < slots.length; slot++) {
        final SlotSource source = slots[slot];
        inputs[slot] = source.neuron == null
            ? external[source.index]
            : signals.get(source.neuron);
      }
      final int sum = neuron.calc(inputs);
      forward.inputs.put(neuron, inputs);
      forward.sums.put(neuron, sum);
      forward.bands.put(neuron, CyberNeuron.bandOf(sum));
      signals.put(neuron, CyberNeuron.bandOf(sum).ordinal());
    }
    return forward;
  }

  /**
   * Assembles {@link CyberNetwork} from neurons the caller already built.
   */
  public static final class Builder {

    private final int externalCount;
    private final Map<CyberNeuron, SlotSource[]> bindings = new LinkedHashMap<>();
    private final Map<CyberNeuron, Integer> layerWeights = new LinkedHashMap<>();
    private final List<CyberNeuron> outputs = new ArrayList<>();

    private Builder(final int externalCount) {
      this.externalCount = externalCount;
    }

    /**
     * Sets the teaching weight of a hidden layer.
     * Every hidden neuron starts at weight {@code 1}, so untouched layers stay
     * equal. The weight divides {@code learningDivider * (depth + 1)}, so a
     * heavier layer spends more of the same downstream share.
     *
     * @param weight  positive layer weight
     * @param neurons neurons that share this weight; at least one
     * @return this builder
     * @throws IllegalArgumentException when {@code weight} is not positive,
     *                                  {@code neurons} is empty, or a neuron is already in a layer
     */
    public Builder layer(final int weight, final CyberNeuron... neurons) {
      if (weight < 1) {
        throw new IllegalArgumentException("Layer weight must be positive");
      }
      if (neurons == null || neurons.length == 0) {
        throw new IllegalArgumentException("A layer needs at least one neuron");
      }
      final Set<CyberNeuron> seen = new HashSet<>();
      for (final CyberNeuron neuron : neurons) {
        requireNonNull(neuron, "neuron must not be null");
        if (!seen.add(neuron) || this.layerWeights.containsKey(neuron)) {
          throw new IllegalArgumentException("Neuron already belongs to a layer");
        }
      }
      for (final CyberNeuron neuron : neurons) {
        this.known(neuron);
        this.layerWeights.put(neuron, weight);
      }
      return this;
    }

    /**
     * Binds one input slot to an external symbol.
     *
     * @param neuron        neuron that reads the symbol
     * @param slot          input slot on {@code neuron}
     * @param externalIndex index in the sample passed to {@link CyberNetwork#recall(int[])}
     * @return this builder
     * @throws IllegalArgumentException when the slot or the external index is outside its range,
     *                                  or the slot is already bound
     */
    public Builder bind(final CyberNeuron neuron, final int slot, final int externalIndex) {
      this.requireSlot(neuron, slot);
      if (externalIndex < 0 || externalIndex >= this.externalCount) {
        throw new IllegalArgumentException(
            "External index is outside 0.." + (this.externalCount - 1));
      }
      this.bindings.get(neuron)[slot] = SlotSource.external(externalIndex);
      return this;
    }

    /**
     * Binds one input slot to the confidence symbol of {@code source}.
     * {@code target} must accept symbols {@code 0..3}.
     *
     * @param source     neuron whose {@link CyberNeuron#signal(int[])} is read
     * @param target     neuron that consumes the symbol
     * @param targetSlot input slot on {@code target}
     * @return this builder
     * @throws IllegalArgumentException when the slot is illegal, already bound,
     *                                  {@code target} cannot address {@code 0..3}, or the two neurons are the same
     */
    public Builder connect(
        final CyberNeuron source,
        final CyberNeuron target,
        final int targetSlot) {
      requireNonNull(source, "source must not be null");
      if (source == target) {
        throw new IllegalArgumentException("A neuron cannot read its own signal");
      }
      this.requireSlot(target, targetSlot);
      if (target.getRowLength() <= ConfidenceDegree.YES.ordinal()) {
        throw new IllegalArgumentException(
            "A neuron that reads a signal needs max value at least "
                + ConfidenceDegree.YES.ordinal());
      }
      this.bindings.get(target)[targetSlot] = SlotSource.ofNeuron(source);
      this.known(source);
      return this;
    }

    /**
     * Marks {@code neuron} as an output, in the order results are returned.
     *
     * @param neuron neuron whose confidence is part of the network result
     * @return this builder
     */
    public Builder output(final CyberNeuron neuron) {
      requireNonNull(neuron, "neuron must not be null");
      if (this.outputs.contains(neuron)) {
        throw new IllegalArgumentException("Output neuron is already declared");
      }
      this.known(neuron);
      this.outputs.add(neuron);
      return this;
    }

    /**
     * Checks that every slot is bound and the links are acyclic.
     *
     * @return the network
     * @throws IllegalStateException when an output is missing, a slot is unbound,
     *                               or the links cycle
     */
    public CyberNetwork build() {
      if (this.outputs.isEmpty()) {
        throw new IllegalStateException("Declare at least one output neuron");
      }
      for (final SlotSource[] slots : this.bindings.values()) {
        for (final SlotSource source : slots) {
          if (source == null) {
            throw new IllegalStateException("Every neuron input slot must be bound");
          }
        }
      }
      final Map<CyberNeuron, SlotSource[]> frozen = new LinkedHashMap<>();
      for (final Map.Entry<CyberNeuron, SlotSource[]> entry : this.bindings.entrySet()) {
        frozen.put(entry.getKey(), entry.getValue().clone());
      }
      final List<CyberNeuron> order = this.topologicalOrder();
      final Map<CyberNeuron, List<Feed>> outgoing = this.outgoingLinks();
      return new CyberNetwork(
          this.externalCount,
          order,
          List.copyOf(this.outputs),
          frozen,
          outgoing,
          Map.copyOf(this.layerWeights),
          this.depths(order, outgoing));
    }

    private Map<CyberNeuron, Integer> depths(
        final List<CyberNeuron> order,
        final Map<CyberNeuron, List<Feed>> outgoing) {
      final Map<CyberNeuron, Integer> depth = new HashMap<>();
      for (final CyberNeuron output : this.outputs) {
        depth.put(output, 0);
      }
      boolean changed = true;
      while (changed) {
        changed = false;
        for (final CyberNeuron neuron : order) {
          for (final Feed feed : outgoing.getOrDefault(neuron, List.of())) {
            final Integer consumerDepth = depth.get(feed.target);
            if (consumerDepth == null) {
              continue;
            }
            final int candidate = consumerDepth + 1;
            final Integer current = depth.get(neuron);
            if (current == null || candidate < current) {
              depth.put(neuron, candidate);
              changed = true;
            }
          }
        }
      }
      return depth;
    }

    private void requireSlot(final CyberNeuron neuron, final int slot) {
      final SlotSource[] slots = this.known(neuron);
      if (slot < 0 || slot >= slots.length) {
        throw new IllegalArgumentException("Input slot is outside 0.." + (slots.length - 1));
      }
      if (slots[slot] != null) {
        throw new IllegalArgumentException("Input slot " + slot + " is already bound");
      }
    }

    private SlotSource[] known(final CyberNeuron neuron) {
      requireNonNull(neuron, "neuron must not be null");
      return this.bindings.computeIfAbsent(
          neuron, ignored -> new SlotSource[neuron.getInputSize()]);
    }

    private Map<CyberNeuron, List<Feed>> outgoingLinks() {
      final Map<CyberNeuron, List<Feed>> outgoing = new LinkedHashMap<>();
      for (final Map.Entry<CyberNeuron, SlotSource[]> entry : this.bindings.entrySet()) {
        final SlotSource[] slots = entry.getValue();
        for (int slot = 0; slot < slots.length; slot++) {
          final CyberNeuron source = slots[slot].neuron;
          if (source != null) {
            outgoing.computeIfAbsent(source, ignored -> new ArrayList<>())
                .add(new Feed(entry.getKey(), slot));
          }
        }
      }
      return outgoing;
    }

    private List<CyberNeuron> topologicalOrder() {
      final Map<CyberNeuron, Integer> pending = new HashMap<>();
      final Map<CyberNeuron, List<CyberNeuron>> readers = new HashMap<>();
      for (final CyberNeuron neuron : this.bindings.keySet()) {
        pending.put(neuron, 0);
        readers.put(neuron, new ArrayList<>());
      }
      for (final Map.Entry<CyberNeuron, SlotSource[]> entry : this.bindings.entrySet()) {
        for (final SlotSource source : entry.getValue()) {
          if (source.neuron != null) {
            pending.merge(entry.getKey(), 1, Integer::sum);
            readers.get(source.neuron).add(entry.getKey());
          }
        }
      }
      final List<CyberNeuron> order = new ArrayList<>();
      final List<CyberNeuron> ready = new ArrayList<>();
      for (final Map.Entry<CyberNeuron, Integer> entry : pending.entrySet()) {
        if (entry.getValue() == 0) {
          ready.add(entry.getKey());
        }
      }
      for (int index = 0; index < ready.size(); index++) {
        final CyberNeuron source = ready.get(index);
        order.add(source);
        for (final CyberNeuron reader : readers.get(source)) {
          final int left = pending.merge(reader, -1, Integer::sum);
          if (left == 0) {
            ready.add(reader);
          }
        }
      }
      if (order.size() != this.bindings.size()) {
        throw new IllegalStateException("Neuron links must be acyclic");
      }
      return List.copyOf(order);
    }
  }

  private static final class SlotSource {
    private final CyberNeuron neuron;
    private final int index;

    private SlotSource(final CyberNeuron neuron, final int index) {
      this.neuron = neuron;
      this.index = index;
    }

    private static SlotSource external(final int index) {
      return new SlotSource(null, index);
    }

    private static SlotSource ofNeuron(final CyberNeuron neuron) {
      return new SlotSource(neuron, 0);
    }
  }

  private static final class Feed {
    private final CyberNeuron target;
    private final int slot;

    private Feed(final CyberNeuron target, final int slot) {
      this.target = target;
      this.slot = slot;
    }
  }

  private static final class Forward {
    private final Map<CyberNeuron, int[]> inputs = new LinkedHashMap<>();
    private final Map<CyberNeuron, Integer> sums = new LinkedHashMap<>();
    private final Map<CyberNeuron, ConfidenceDegree> bands = new LinkedHashMap<>();
  }
}
