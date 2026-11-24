package com.igormaznitsa.cyberneuro.core;

import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.MAY_BE_YES;
import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.NO;
import static com.igormaznitsa.cyberneuro.core.ConfidenceDegree.YES;
import static com.igormaznitsa.cyberneuro.core.LearnStrategy.SEQUENTIAL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class DigitSymbolsNetworkTest {

  private static final int PIXELS = 64;
  private static final int SIDE = 8;
  private static final int STRIPS = 4;
  private static final int STRIP_PIXELS = 16;
  private static final int DIGITS = 10;
  private static final int MAX_PASSES = 500;
  private static final int LEARNING_DIVIDER = 1;
  private static final int VARIANT_MARGIN = 12;
  private static final int MAX_SELF_DISTANCE = 12;
  private static final int VARIANT_DIVIDER = 64;
  private static final int VARIANT_PASSES = 20;
  private static final int REPAIR_PASSES = 40;

  public static void main(final String[] args) {
    new DigitSymbolsNetworkTest().openSketch();
  }

  @Disabled("Manual drawing form. Start DigitSymbolsNetworkTest.main.")
  @Test
  void drawsAndRecognizesInASketch() {
    this.openSketch();
  }

  @Test
  @Timeout(60)
  void recognizesEightByEightDigits() {
    final List<Glyph> glyphs = this.loadGlyphs("/digits-8x8.txt", "digit", true);
    final List<Glyph> probes = this.loadGlyphs("/digits-8x8-probes.txt", "probe", false);
    final CyberNetwork network = this.buildNetwork();

    System.out.println(network.makeDotDiagram());

    assertTrue(!this.teachingLessons(glyphs).isEmpty());
    this.teachUntilRecognized(network, glyphs);
    this.assertEachGlyph(network, glyphs);
    this.assertProbesDiffer(glyphs, probes);
    this.assertEachGlyph(network, probes);
  }

  private void openSketch() {
    final CyberNetwork network = this.buildNetwork();
    this.teachUntilRecognized(network, this.loadGlyphs("/digits-8x8.txt", "digit", true));
    final CountDownLatch closed = new CountDownLatch(1);
    try {
      SwingUtilities.invokeAndWait(() -> this.showSketch(network, closed));
      closed.await();
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (final InvocationTargetException ex) {
      throw new IllegalStateException("Sketch form failed to open", ex);
    }
  }

  private void showSketch(final CyberNetwork network, final CountDownLatch closed) {
    final JLabel verdict = new JLabel(" ");
    verdict.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
    final Sketch sketch = new Sketch(network, verdict);
    final JFrame frame = new JFrame("Draw a digit");
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(final WindowEvent event) {
        closed.countDown();
      }
    });
    frame.add(new JLabel("Left drag draws. Right drag erases."), BorderLayout.NORTH);
    frame.add(sketch, BorderLayout.CENTER);
    frame.add(this.sketchControls(sketch, verdict), BorderLayout.SOUTH);
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }

  private JPanel sketchControls(final Sketch sketch, final JLabel verdict) {
    final JPanel controls = new JPanel(new BorderLayout());
    final JButton clear = new JButton("Clear");
    clear.addActionListener(event -> sketch.clear());
    controls.add(verdict, BorderLayout.CENTER);
    controls.add(clear, BorderLayout.EAST);
    return controls;
  }

  private void teachUntilRecognized(final CyberNetwork network, final List<Glyph> glyphs) {
    final List<Glyph> lessons = this.teachingLessons(glyphs);
    this.teachEtalonsUntilRecognized(network, glyphs, MAX_PASSES);

    for (int pass = 0; pass < VARIANT_PASSES; pass++) {
      this.teachAll(network, lessons, VARIANT_DIVIDER);
      this.teachEtalonsUntilRecognized(network, glyphs, REPAIR_PASSES);
    }
  }

  private void teachEtalonsUntilRecognized(
      final CyberNetwork network,
      final List<Glyph> glyphs,
      final int passes) {
    for (int pass = 0; pass < passes && !this.recognizesAll(network, glyphs); pass++) {
      this.teachAll(network, glyphs, LEARNING_DIVIDER);
    }
  }

  private void teachAll(
      final CyberNetwork network,
      final List<Glyph> lessons,
      final int divider) {
    for (final Glyph lesson : lessons) {
      network.teach(lesson.getPixels(), this.bandsFor(lesson.getDigit()), SEQUENTIAL, divider);
    }
  }

  private List<Glyph> teachingLessons(final List<Glyph> glyphs) {
    return glyphs.stream()
        .flatMap(glyph -> this.variants(glyph, glyphs).stream())
        .collect(toList());
  }

  private List<Glyph> variants(final Glyph glyph, final List<Glyph> glyphs) {
    final int[] pixels = glyph.getPixels();
    final List<int[]> samples = new ArrayList<>();
    this.keepVariant(samples, this.rotateDegrees(pixels, 15), glyph.getDigit(), glyphs);
    this.keepVariant(samples, this.rotateDegrees(pixels, -15), glyph.getDigit(), glyphs);
    this.keepVariant(samples, this.rotateDegrees(pixels, 20), glyph.getDigit(), glyphs);
    this.keepVariant(samples, this.rotateDegrees(pixels, -20), glyph.getDigit(), glyphs);
    this.keepVariant(samples, this.mirrorLeftRight(pixels), glyph.getDigit(), glyphs);
    this.keepVariant(samples, this.mirrorUpDown(pixels), glyph.getDigit(), glyphs);
    this.keepVariant(samples, this.scaleBy(pixels, 1.2), glyph.getDigit(), glyphs);
    this.keepVariant(samples, this.scaleBy(pixels, 0.9), glyph.getDigit(), glyphs);
    return samples.stream()
        .map(sample -> new Glyph(glyph.getDigit(), sample))
        .collect(toList());
  }

  private void keepVariant(
      final List<int[]> samples,
      final int[] sample,
      final int digit,
      final List<Glyph> glyphs) {
    if (samples.stream().anyMatch(existing -> Arrays.equals(existing, sample))) {
      return;
    }
    final int distance = this.hamming(sample, glyphs.get(digit).getPixels());
    if (distance == 0 || distance > MAX_SELF_DISTANCE) {
      return;
    }
    if (this.matchesAnotherDigit(sample, digit, glyphs)) {
      return;
    }
    samples.add(sample);
  }

  private boolean matchesAnotherDigit(
      final int[] sample,
      final int digit,
      final List<Glyph> glyphs) {
    return IntStream.range(0, glyphs.size())
        .filter(index -> index != digit)
        .anyMatch(index -> this.hamming(sample, glyphs.get(index).getPixels()) <= VARIANT_MARGIN);
  }

  private int hamming(final int[] left, final int[] right) {
    return (int) IntStream.range(0, left.length)
        .filter(index -> left[index] != right[index])
        .count();
  }

  private int[] rotateDegrees(final int[] pixels, final int degrees) {
    final double radians = Math.toRadians(degrees);
    final double cosine = Math.cos(radians);
    final double sine = Math.sin(radians);
    final int[] rotated = new int[PIXELS];
    for (int row = 0; row < SIDE; row++) {
      for (int column = 0; column < SIDE; column++) {
        final double dx = column - 3.5;
        final double dy = row - 3.5;
        final int sourceColumn = (int) Math.round(cosine * dx + sine * dy + 3.5);
        final int sourceRow = (int) Math.round(-sine * dx + cosine * dy + 3.5);
        if (sourceColumn < 0 || sourceColumn >= SIDE || sourceRow < 0 || sourceRow >= SIDE) {
          continue;
        }
        rotated[row * SIDE + column] = pixels[sourceRow * SIDE + sourceColumn];
      }
    }
    return rotated;
  }

  private int[] mirrorLeftRight(final int[] pixels) {
    final int[] mirrored = new int[PIXELS];
    for (int row = 0; row < SIDE; row++) {
      for (int column = 0; column < SIDE; column++) {
        mirrored[row * SIDE + (SIDE - 1 - column)] = pixels[row * SIDE + column];
      }
    }
    return mirrored;
  }

  private int[] mirrorUpDown(final int[] pixels) {
    final int[] mirrored = new int[PIXELS];
    for (int row = 0; row < SIDE; row++) {
      for (int column = 0; column < SIDE; column++) {
        mirrored[(SIDE - 1 - row) * SIDE + column] = pixels[row * SIDE + column];
      }
    }
    return mirrored;
  }

  private int[] scaleBy(final int[] pixels, final double factor) {
    final int[] scaled = new int[PIXELS];
    for (int row = 0; row < SIDE; row++) {
      for (int column = 0; column < SIDE; column++) {
        final int sourceColumn = (int) Math.round((column - 3.5) / factor + 3.5);
        final int sourceRow = (int) Math.round((row - 3.5) / factor + 3.5);
        if (sourceColumn < 0 || sourceColumn >= SIDE || sourceRow < 0 || sourceRow >= SIDE) {
          continue;
        }
        scaled[row * SIDE + column] = pixels[sourceRow * SIDE + sourceColumn];
      }
    }
    return scaled;
  }

  private void assertEachGlyph(final CyberNetwork network, final List<Glyph> glyphs) {
    final String report = this.report(network, glyphs);
    for (final Glyph glyph : glyphs) {
      assertEquals(
          glyph.getDigit(),
          this.recognizedDigit(network.recall(glyph.getPixels())),
          report);
    }
  }

  private boolean recognizesAll(final CyberNetwork network, final List<Glyph> glyphs) {
    return this.recognizedCount(network, glyphs) == glyphs.size();
  }

  private int recognizedCount(final CyberNetwork network, final List<Glyph> glyphs) {
    return (int) glyphs.stream()
        .filter(
            glyph -> this.recognizedDigit(network.recall(glyph.getPixels())) == glyph.getDigit())
        .count();
  }

  private int recognizedDigit(final List<ConfidenceDegree> bands) {
    final List<Integer> yes = IntStream.range(0, bands.size())
        .filter(index -> bands.get(index) == YES)
        .boxed()
        .collect(toList());
    final boolean hasWeakYes = bands.stream().anyMatch(band -> band == MAY_BE_YES);
    if (yes.size() != 1 || hasWeakYes) {
      return -1;
    }
    return yes.get(0);
  }

  private List<ConfidenceDegree> bandsFor(final int digit) {
    return IntStream.range(0, DIGITS)
        .mapToObj(index -> index == digit ? YES : NO)
        .collect(toList());
  }

  private void assertProbesDiffer(final List<Glyph> glyphs, final List<Glyph> probes) {
    for (final Glyph probe : probes) {
      assertTrue(this.differs(glyphs.get(probe.getDigit()).getPixels(), probe.getPixels()));
    }
  }

  private boolean differs(final int[] left, final int[] right) {
    return IntStream.range(0, left.length).anyMatch(index -> left[index] != right[index]);
  }

  private String report(final CyberNetwork network, final List<Glyph> glyphs) {
    return glyphs.stream()
        .map(glyph -> glyph.getDigit() + " -> " + network.recall(glyph.getPixels()))
        .collect(joining("\n"));
  }

  private CyberNetwork buildNetwork() {
    final List<CyberNeuron> strips = this.layer(DIGITS * STRIPS, STRIP_PIXELS, 1);
    final List<CyberNeuron> mixed = this.layer(DIGITS, STRIPS, YES.ordinal());
    final List<CyberNeuron> codes = this.layer(DIGITS, 1, YES.ordinal());
    final List<CyberNeuron> outputs = this.layer(DIGITS, 1, YES.ordinal());
    return this.declareOutputs(
        this.connectAligned(
            this.connectAligned(
                this.connectStrips(
                    this.bindStrips(CyberNetwork.externalInputs(PIXELS), strips),
                    strips,
                    mixed),
                mixed,
                codes),
            codes,
            outputs),
        outputs).build();
  }

  private List<CyberNeuron> layer(final int count, final int inputs, final int maxValue) {
    return IntStream.range(0, count)
        .mapToObj(ignored -> this.cleared(inputs, maxValue))
        .collect(toList());
  }

  private CyberNeuron cleared(final int inputs, final int maxValue) {
    final CyberNeuron neuron = CyberNeuron.of(inputs, maxValue);
    neuron.fill(new byte[inputs * neuron.getRowLength()]);
    return neuron;
  }

  private CyberNetwork.Builder bindStrips(
      final CyberNetwork.Builder builder,
      final List<CyberNeuron> strips) {
    CyberNetwork.Builder current = builder;
    for (int index = 0; index < strips.size(); index++) {
      final int band = index % STRIPS;
      for (int slot = 0; slot < STRIP_PIXELS; slot++) {
        final int row = band * 2 + slot / SIDE;
        final int column = slot % SIDE;
        current = current.bind(strips.get(index), slot, row * SIDE + column);
      }
    }
    return current;
  }

  private CyberNetwork.Builder connectStrips(
      final CyberNetwork.Builder builder,
      final List<CyberNeuron> strips,
      final List<CyberNeuron> mixed) {
    CyberNetwork.Builder current = builder;
    for (int digit = 0; digit < mixed.size(); digit++) {
      for (int band = 0; band < STRIPS; band++) {
        current = current.connect(strips.get(digit * STRIPS + band), mixed.get(digit), band);
      }
    }
    return current;
  }

  private CyberNetwork.Builder connectAligned(
      final CyberNetwork.Builder builder,
      final List<CyberNeuron> sources,
      final List<CyberNeuron> targets) {
    CyberNetwork.Builder current = builder;
    for (int index = 0; index < sources.size(); index++) {
      current = current.connect(sources.get(index), targets.get(index), 0);
    }
    return current;
  }

  private CyberNetwork.Builder declareOutputs(
      final CyberNetwork.Builder builder,
      final List<CyberNeuron> outputs) {
    CyberNetwork.Builder current = builder;
    for (final CyberNeuron output : outputs) {
      current = current.output(output);
    }
    return current;
  }

  private List<Glyph> loadGlyphs(
      final String resource,
      final String label,
      final boolean alphabet) {
    final InputStream stream = DigitSymbolsNetworkTest.class.getResourceAsStream(resource);
    if (stream == null) {
      throw new IllegalStateException(resource + " is missing");
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
      return this.parseGlyphs(reader.lines().collect(toList()), label, alphabet);
    } catch (final IOException ex) {
      throw new IllegalStateException(resource + " cannot be read", ex);
    }
  }

  private List<Glyph> parseGlyphs(
      final List<String> lines,
      final String label,
      final boolean alphabet) {
    final List<Glyph> glyphs = new ArrayList<>();
    int index = 0;
    while (index < lines.size()) {
      final String header = lines.get(index).strip();
      index++;
      if (header.isEmpty() || header.startsWith("//")) {
        continue;
      }
      glyphs.add(this.glyphAfter(header, label, lines, index));
      index += SIDE;
    }
    if (alphabet) {
      this.requireCompleteAlphabet(glyphs);
    }
    return glyphs;
  }

  private Glyph glyphAfter(
      final String header,
      final String label,
      final List<String> lines,
      final int firstRow) {
    final String prefix = label + " ";
    if (!header.startsWith(prefix)) {
      throw new IllegalArgumentException(
          "Expected a " + label + " header but got '" + header + "'");
    }
    final int digit = Integer.parseInt(header.substring(prefix.length()));
    final int[] pixels = new int[PIXELS];
    for (int row = 0; row < SIDE; row++) {
      this.copyRow(lines.get(firstRow + row).strip(), row, pixels);
    }
    return new Glyph(digit, pixels);
  }

  private void copyRow(final String row, final int rowIndex, final int[] pixels) {
    if (row.length() != SIDE) {
      throw new IllegalArgumentException("Expected 8 pixels but got '" + row + "'");
    }
    for (int column = 0; column < SIDE; column++) {
      pixels[rowIndex * SIDE + column] = this.pixel(row.charAt(column));
    }
  }

  private int pixel(final char mark) {
    if (mark == 'X') {
      return 1;
    }
    if (mark == '.') {
      return 0;
    }
    throw new IllegalArgumentException("Pixel must be X or . but got '" + mark + "'");
  }

  private void requireCompleteAlphabet(final List<Glyph> glyphs) {
    final List<Integer> digits = glyphs.stream().map(Glyph::getDigit).collect(toList());
    assertEquals(IntStream.range(0, DIGITS).boxed().collect(toList()), digits);
  }

  private static final class Glyph {
    private final int digit;
    private final int[] pixels;

    private Glyph(final int digit, final int[] pixels) {
      this.digit = digit;
      this.pixels = pixels;
    }

    private int getDigit() {
      return this.digit;
    }

    private int[] getPixels() {
      return this.pixels;
    }
  }

  private final class Sketch extends JPanel {
    private static final int CELL = 40;

    private final int[] pixels = new int[PIXELS];
    private final CyberNetwork network;
    private final JLabel verdict;
    private int ink = 1;

    private Sketch(final CyberNetwork network, final JLabel verdict) {
      this.network = network;
      this.verdict = verdict;
      this.setPreferredSize(new Dimension(SIDE * CELL + 1, SIDE * CELL + 1));
      this.setBackground(Color.WHITE);
      final MouseAdapter strokes = this.strokes();
      this.addMouseListener(strokes);
      this.addMouseMotionListener(strokes);
      this.publishVerdict();
    }

    private void clear() {
      Arrays.fill(this.pixels, 0);
      this.repaint();
      this.publishVerdict();
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
      super.paintComponent(graphics);
      for (int row = 0; row < SIDE; row++) {
        for (int column = 0; column < SIDE; column++) {
          this.paintCell(graphics, row, column);
        }
      }
    }

    private void paintCell(final Graphics graphics, final int row, final int column) {
      final int x = column * CELL;
      final int y = row * CELL;
      graphics.setColor(this.pixels[row * SIDE + column] == 1 ? Color.BLACK : Color.WHITE);
      graphics.fillRect(x, y, CELL, CELL);
      graphics.setColor(Color.GRAY);
      graphics.drawRect(x, y, CELL, CELL);
    }

    private MouseAdapter strokes() {
      return new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent event) {
          Sketch.this.beginStroke(event);
        }

        @Override
        public void mouseDragged(final MouseEvent event) {
          Sketch.this.paintAt(event);
        }
      };
    }

    private void beginStroke(final MouseEvent event) {
      this.ink = SwingUtilities.isRightMouseButton(event) ? 0 : 1;
      this.paintAt(event);
    }

    private void paintAt(final MouseEvent event) {
      final int column = event.getX() / CELL;
      final int row = event.getY() / CELL;
      if (column < 0 || column >= SIDE || row < 0 || row >= SIDE) {
        return;
      }
      this.pixels[row * SIDE + column] = this.ink;
      this.repaint();
      this.publishVerdict();
    }

    private void publishVerdict() {
      final List<ConfidenceDegree> bands = this.network.recall(this.pixels);
      final int digit = DigitSymbolsNetworkTest.this.recognizedDigit(bands);
      final String name = digit < 0 ? "unrecognized" : "digit " + digit;
      this.verdict.setText("<html>" + name + "<br>" + bands + "</html>");
    }
  }
}
