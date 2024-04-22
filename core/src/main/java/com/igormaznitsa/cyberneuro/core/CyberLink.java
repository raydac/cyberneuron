package com.igormaznitsa.cyberneuro.core;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

public final class CyberLink implements Comparable<CyberLink> {
  private final HasOutput source;
  private final int sourceIndex;
  private final HasInput target;
  private final int targetIndex;

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

  public HasOutput source() {
    return source;
  }

  public int sourceIndex() {
    return sourceIndex;
  }

  public HasInput target() {
    return target;
  }

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

  @Override
  public int compareTo(final CyberLink that) {
    return Integer.compare(this.targetIndex, that.targetIndex);
  }
}
