package com.igormaznitsa.cyberneuro.core;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

public record CyberLink(HasOutput source, int sourceIndex, HasInput target, int targetIndex) {
  public CyberLink {
    requireNonNull(source);
    requireNonNull(target);
    if (source.equals(target)) {
      throw new IllegalArgumentException("Can't link itself");
    }
    if (!source.isOutputIndexValid(sourceIndex)) {
      throw new IndexOutOfBoundsException("Output index is invalid");
    }
    if (!target.isInputIndexValid(targetIndex)) {
      throw new IndexOutOfBoundsException("Input index is invalid");
    }
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
}
