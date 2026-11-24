package com.igormaznitsa.cyberneuro.core;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

/**
 * Directed connection from one output slot to one input slot.
 *
 * <p>Equality ignores {@link #sourceIndex()} and uses the source, the target,
 * and {@link #targetIndex()}. Ordering compares target slots only.
 */
public final class CyberLink implements Comparable<CyberLink> {
  private final HasOutput source;
  private final int sourceIndex;
  private final HasInput target;
  private final int targetIndex;

  /**
   * Connects {@code source}'s output slot to {@code target}'s input slot.
   *
   * @param source      element that produces the value
   * @param sourceIndex output slot on {@code source}
   * @param target      element that consumes the value
   * @param targetIndex input slot on {@code target}
   * @throws IllegalArgumentException  when {@code source} and {@code target} are the same element
   * @throws IndexOutOfBoundsException when either slot index is outside that element
   * @throws NullPointerException      when {@code source} or {@code target} is null
   */
  public CyberLink(
      final HasOutput source,
      final int sourceIndex,
      final HasInput target,
      final int targetIndex) {
    this.source = requireNonNull(source);
    this.target = requireNonNull(target);
    if (source.equals(target)) {
      throw new IllegalArgumentException("Can't link itself");
    }
    if (!source.isOutputIndexValid(sourceIndex)) {
      throw new IndexOutOfBoundsException("Output index is invalid");
    }
    if (!target.isInputIndexValid(targetIndex)) {
      throw new IndexOutOfBoundsException("Input index is invalid");
    }

    this.sourceIndex = sourceIndex;
    this.targetIndex = targetIndex;
  }

  /**
   * Element that produces the value on this link.
   *
   * @return source passed to the constructor
   */
  public HasOutput source() {
    return source;
  }

  /**
   * Output slot on {@link #source()}.
   *
   * @return zero-based slot index
   */
  public int sourceIndex() {
    return sourceIndex;
  }

  /**
   * Element that consumes the value on this link.
   *
   * @return target passed to the constructor
   */
  public HasInput target() {
    return target;
  }

  /**
   * Input slot on {@link #target()}.
   *
   * @return zero-based slot index
   */
  public int targetIndex() {
    return targetIndex;
  }

  @Override
  public String toString() {
    return "CyberLink[" +
        "source=" + source + ", " +
        "sourceIndex=" + sourceIndex + ", " +
        "target=" + target + ", " +
        "targetIndex=" + targetIndex + ']';
  }


  @Override
  public boolean equals(final Object that) {
    if (this == that) {
      return true;
    }
    if (that == null || getClass() != that.getClass()) {
      return false;
    }
    CyberLink cyberLink = (CyberLink) that;
    return this.targetIndex == cyberLink.targetIndex
        && Objects.equals(this.source, cyberLink.source)
        && Objects.equals(this.target, cyberLink.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.source, this.target, this.targetIndex);
  }

  /**
   * Orders links by {@link #targetIndex()} only.
   *
   * @param that other link
   * @return negative, zero, or positive as this target slot compares to {@code that}
   */
  @Override
  public int compareTo(final CyberLink that) {
    return Integer.compare(this.targetIndex, that.targetIndex);
  }
}
