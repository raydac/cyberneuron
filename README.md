# Cyberneuron

Java implementation of the cyberneuron from Polikarpov, Dergachev, Rumyantsev and
Golubchikov, [A new model of artificial neuron: cyberneuron and its use](https://arxiv.org/pdf/0907.0229.pdf) (2009).

A cyberneuron does not multiply inputs by weights. Each input is a symbol that selects one cell in its own substitution
row, and the neuron output is the sum of those cells. Teaching rewrites only the cells the current sample addressed. The
same cell rule teaches a single neuron. A stack does not send a derivative through a table index: a later table uses a
value as an index, so inside a cell the slope is zero. The paper states that limit directly. What travels instead is the
signed gap described in [Teaching a stack](#teaching-a-stack).

The module is Java 11. Build and test with `mvn22 -pl core test`.

## One neuron

`CyberNeuron.of(inputSize, maxValue)` builds `inputSize` rows. Every row has `maxValue + 1` signed bytes. An input
vector is legal when it has one symbol per row and each symbol is in `0..maxValue`.

```text
output = sbox[0][input[0]] + sbox[1][input[1]] + ... + sbox[n-1][input[n-1]]
```

`calc` returns that sum. `check` places it on a fixed scale:

| Sum     | Band         | `signal()` |
|---------|--------------|------------|
| ≤ 25    | `NO`         | 0          |
| 26..63  | `MAY_BE_NO`  | 1          |
| 64..102 | `MAY_BE_YES` | 2          |
| ≥ 103   | `YES`        | 3          |

`matches` is the recognized half: `MAY_BE_YES` or `YES`.

Rows are independent. The neuron can memorize a pattern, because that pattern addresses a private set of cells, and it
can implement AND, OR, and NOT on small alphabets. It cannot implement a function that is not a sum of per-input
lookups. XOR is the clear case: any cells that make `(0,1)` and `(1,0)` high also make `(0,0)` or `(1,1)` high. The same
wall stops `(a AND b) OR (c AND d)` when each variable is its own input. Interaction needs several bits packed into one
index, or hidden neurons that already differ and that each see those bits.

### Teaching one neuron

```java
CyberNeuron neuron = CyberNeuron.of(2, 1);
neuron.teach(new int[] {1, 1}, LearnStrategy.SEQUENTIAL, ConfidenceDegree.YES);
neuron.teach(new int[] {0, 0}, LearnStrategy.SEQUENTIAL, ConfidenceDegree.NO);
```

`teach` computes the sum. When the band is already the requested one, it returns. Otherwise it measures the gap to the
edge of that band and asks the strategy to spend that many `+1` or `-1` steps on the addressed cells only. Other cells
stay as they are. `SEQUENTIAL` walks the rows in order and wraps. `RANDOM` picks a row for each step, so two neurons
taught on the same sample can mark different cells.

The fourth argument is the paper's learning divider. The step count is the gap divided by that number, and at least one
step while the sum is still outside the band. Divider `4` is the rate in the paper's experiments (`k = 0.25`). Divider
`1`, the three-argument call, spends the whole gap.

```java
neuron.teach(new int[] {1, 1}, LearnStrategy.SEQUENTIAL, ConfidenceDegree.YES, 4);
```

A locked neuron rejects `fill` and `teach`.

## A stack the caller assembles

`CyberNetwork` is built from neurons that already exist. Each input slot is bound once, either to an external symbol or
to the confidence of an earlier neuron. A slot that reads a confidence needs `maxValue` at least 3, because `signal()`
is `0..3`. The links must be acyclic, and at least one neuron is declared as an output.

```java
CyberNeuron hidden = CyberNeuron.of(1, 1);
CyberNeuron decision = CyberNeuron.of(1, ConfidenceDegree.YES.ordinal());
CyberNetwork network = CyberNetwork.externalInputs(1)
        .layer(1, hidden)
        .bind(hidden, 0, 0)
        .connect(hidden, decision, 0)
        .output(decision)
        .build();

network.teach(new int[] {0},List.of(ConfidenceDegree.YES),LearnStrategy.SEQUENTIAL,1);
        network.teach(new int[] {1},List.of(ConfidenceDegree.NO),LearnStrategy.SEQUENTIAL,1);
List<ConfidenceDegree> bands = network.recall(new int[] {0});
```

`recall` returns one band per output, in the order the outputs were declared. `teach` takes the external sample, one
expected band per output, the cell-walking strategy, and the learning divider. `makeDotDiagram()` returns a Graphviz
`digraph`: external inputs are red boxes, hidden neurons are green ovals, and output neurons are green boxes. A slot
index labels each edge into a neuron that has more than one input.

### Teaching a stack

`teach` records one forward pass, decides a gap for every neuron that played a part, and only then rewrites cells.
The snapshot is what the correction reads, so a cell changed on this sample does not affect the rest of the same walk.

1. **Output error.** For each output whose band is not the requested one, the error is the signed gap from the current
   sum to the edge of that band. A sum already inside the band contributes nothing. A positive gap raises cells. A
   negative gap lowers them. Lowering alone cannot lift a zeroed table into `YES`.

2. **Backward walk.** Neurons are visited from the outputs toward the inputs. A hidden neuron is updated only when a
   consumer it feeds still has a gap.
   - Another confidence symbol is tried against the consumer. If that symbol's cell moves the consumer's sum toward
     the gap, the hidden neuron is taught toward that confidence. The cell values are what choose the symbol.
   - Otherwise each input that fired takes an equal share of the consumer's gap, `downstream / inputCount`. When that
     integer share is 0 but a gap remains, the nudge keeps the gap's sign.

3. **Step size.** The divider applied to a neuron is `learningDivider * (depth + 1) / layerWeight`, and at least 1.
   Depth is the shortest hop count from that neuron to an output. Hidden neurons start at weight 1, so layers left
   untouched stay equal and match the previous hop divider. `layer(weight, neurons)` on the builder sets another
   positive weight; a heavier layer spends more of the same share. Outputs stay at weight 1, so their
   divider remains `learningDivider`. Omitting `layer` is the same as weight 1.

Declare only the last neurons as outputs. An output is trained to the band the caller named. It does not also relay
the gap of some further consumer.

This is credit assignment for the cells that fired. It is not a derivative through the index: inside a cell the slope
is still zero, and the next layer only sees the confidence symbol. Two hidden neurons built by `of` with the same
shape start as copies of one table, and identical inputs keep them copies. A chain learns a map such as NOT, including a
chain of eight neurons. Two outputs can be taught together when each has its own detectors: on two bits, one head
learns OR and the other learns AND. Detectors shared by both heads receive both gaps and can cancel. Four separate
detectors also learn a door rule: grant entry only when the badge is present, the hour is inside the window, the lock
is released, and a guard is on duty. A flat sum of separate bits still cannot learn XOR. Pack those bits into one symbol
when one neuron must store the interaction.

## Where capacity lives

The paper's capacity comes from wide symbols: a byte index, or the 24-bit chunks used for the virus-signature scanner. A
single row with a large alphabet can store many patterns, and teaching one pattern touches only one cell per row, so
older patterns usually survive.

A confidence wire carries only `0..3`. Keep wide alphabets on the first tables when the task is pattern memory. Packing
several bits into one index is what lets a single row store an interaction such as XOR.
