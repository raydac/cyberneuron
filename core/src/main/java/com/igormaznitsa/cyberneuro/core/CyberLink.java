package com.igormaznitsa.cyberneuro.core;

import java.util.Objects;

public record CyberLink(CyberNeuron source, CyberNeuron target, int targetInputIndex) {
  public CyberLink {
    Objects.requireNonNull(source);
    Objects.requireNonNull(target);
    if (source.equals(target)) {
      throw new IllegalArgumentException("Source neuron is equals target neuron");
    }
    if (targetInputIndex < 0) {
      throw new IllegalArgumentException("Negative input index");
    }
    if (targetInputIndex >= target.getInputSize()) {
      throw new IndexOutOfBoundsException("Input index is out of bound");
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
    return targetInputIndex == cyberLink.targetInputIndex &&
        Objects.equals(source, cyberLink.source) &&
        Objects.equals(target, cyberLink.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(source, target, targetInputIndex);
  }
}
