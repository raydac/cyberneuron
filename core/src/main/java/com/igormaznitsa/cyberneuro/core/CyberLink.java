package com.igormaznitsa.cyberneuro.core;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

public record CyberLink(HasOutput source, HasInput target, int targetInputIndex) {
  public CyberLink {
    requireNonNull(source);
    requireNonNull(target);
    if (source.equals(target)) {
      throw new IllegalArgumentException("Can't link itself");
    }
    if (!target.isInputIndexValid(targetInputIndex)) {
      throw new IndexOutOfBoundsException("Input index is out of bounds");
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
    return this.targetInputIndex == cyberLink.targetInputIndex
        && Objects.equals(this.source, cyberLink.source)
        && Objects.equals(this.target, cyberLink.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.source, this.target, this.targetInputIndex);
  }
}
