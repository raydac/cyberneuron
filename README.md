# Cyberneuron

Java implementation of the cyberneuron from Polikarpov, Dergachev, Rumyantsev and
Golubchikov, [A new model of artificial neuron: cyberneuron and its use](https://arxiv.org/pdf/0907.0229.pdf) (2009).

A cyberneuron does not multiply inputs by weights. Each input is a symbol that selects one cell in its own substitution
row, and the neuron output is the sum of those cells. Teaching rewrites only the cells the current sample addressed. The
same rule teaches a single neuron and the two-layer tree in this project. There is no backpropagation: a later table
uses a value as an index, so there is no local slope to send backward. The paper states that limit directly. A later
note by Polikarpov grows a cyberneuron tree by trying wirings and keeping those that raise a balanced recognition score.
`CyberTree` is that idea, on boolean features.

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
index, which is what the tree does.

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

## A graph of neurons

`CyberNet` is a directed graph of neurons, input pins, and output pins. Each input slot accepts one link. `activate`
writes sample symbol `i` onto the `i`-th input pin, in the order the pins were added, and returns one value per output
pin.

A neuron on that path contributes its raw sum. The next neuron uses the incoming number as a row index. A sum outside
`0..maxValue` is rejected. The value that is always a legal index of a confidence-scale row is `signal()`, which is only
`0..3`. Build the downstream neuron with `maxValue` at least `ConfidenceDegree.YES.ordinal()` (3).

The graph must be acyclic. A cycle makes activation run without end.

```java
CyberNet net = new CyberNet();
CyberNetInputPin in0 = net.addInputPin();
CyberNetInputPin in1 = net.addInputPin();
CyberNeuron neuron = CyberNeuron.of(2, 1);
CyberNetOutputPin out = net.addOutput();
net.put(neuron);
net.link(in0, neuron, 0);
net.link(in1, neuron, 1);
net.link(neuron, out);
int[] sum = net.activate(new int[] {1, 1});
```

`makeDotDiagram()` returns a Graphviz `digraph` of the same graph.

## Teaching the two-layer tree

`CyberTree` is the multilayer model in this project. Both layers are ordinary cyberneurons. The first layer holds
detectors. Each detector has a single input: a chosen group of raw bits packed into one integer, so one cell stands for
one joint pattern of those bits. The second layer is one root neuron. Its single input is the yes/no pattern of the
detectors, packed the same way. The root is what `accepts` reads.

Features are booleans. Create the tree with the vector width, then teach it with labeled examples:

```java
List<CyberTree.Example> examples = List.of(
    CyberTree.Example.of(false, false, false),
    CyberTree.Example.of(true, false, true),
    CyberTree.Example.of(true, true, false),
    CyberTree.Example.of(false, true, true)
);

CyberTree tree = CyberTree.ofBitCount(2);
double score = tree.grow(examples, CyberTree.Growth.arity(2).detectors(1));
boolean recognized = tree.accepts(new boolean[] {true, false});
```

`Example.of` takes the class bit first, then the features. `grow` returns the recognition score of the list it was
given. `1.0` means every example is classified correctly. A later `grow` on the same tree keeps the detectors already
committed and continues. `asText()` prints the bit indexes each detector reads, which is the wiring the search kept.

`arity` is how many raw bits one detector sees at once. `detectors` is the maximum number of first-layer tables. Arity
is at most 12, and the search refuses more than 100 000 candidate tuples.

### One growth step

`grow` repeats the following until the score is perfect, the detector limit is reached, or no candidate raises the
score.

1. Enumerate every combination of `arity` input bits that is not already a detector.
2. Train a fresh detector on the whole list. Each example is packed into the index of that tuple. The table starts at
   zero. For each cell, count positive and negative hits. When positives strictly outnumber negatives, that cell is
   taught once with `SEQUENTIAL` toward `YES`, which lifts it to at least 103. Otherwise the cell stays 0, which the
   scale already reads as `NO`.
3. Train a fresh root the same way. Its index is which of the current detectors fire on the example. Its label is still
   the example's original class.
4. Score the root. Commit the candidate only when the score is strictly higher, then train the root again on the
   committed set.

Detectors are frozen after they are committed. They are not revised by the root. The root is rewritten from scratch
after every committed detector, always against the original labels. That is the whole credit assignment: the second
layer learns the combination by table lookup, and the first layer is whatever wiring made that lookup more accurate.

### What the width has to cover

| Task                      | Call                    | Result                                                                                                          |
|---------------------------|-------------------------|-----------------------------------------------------------------------------------------------------------------|
| XOR of two bits           | `arity(2).detectors(1)` | One joint table memorizes the four patterns.                                                                    |
| `(a AND b) OR (c AND d)`  | `arity(2).detectors(2)` | Two AND detectors. The root stores OR of their yes/no bits.                                                     |
| `(a AND b) XOR (c AND d)` | `arity(2).detectors(2)` | The same two detectors. The root stores XOR of their yes/no bits.                                               |
| 3-bit parity              | `arity(2).detectors(3)` | Does not reach a perfect score. Every pair is split evenly by the third bit, so every cell ties and stays `NO`. |
| 3-bit parity              | `arity(3).detectors(1)` | One table sees all three bits and memorizes parity.                                                             |

### The score

The score is the balanced share from the cyberneuron-tree note. With
`k = (number of negatives) / (number of positives)`:

```text
(k * truePositive + trueNegative)
    / (k * truePositive + trueNegative + k * falsePositive + falseNegative)
```

An empty tree predicts every sample negative. A tie inside a cell is treated as negative, because the cell is left at
zero.

## Where capacity lives

The paper's capacity comes from wide symbols: a byte index, or the 24-bit chunks used for the virus-signature scanner. A
single row with a large alphabet can store many patterns, and teaching one pattern touches only one cell per row, so
older patterns usually survive.

The tree spends that idea differently. Packing `arity` bits into one index is what creates the interaction. The wires
between the two layers are only yes/no. Four-level `signal()` values are the same kind of narrow glue. Keep wide
alphabets on the first tables when the task is pattern memory. Use the tree when the task is a boolean function that one
additive neuron cannot separate.
