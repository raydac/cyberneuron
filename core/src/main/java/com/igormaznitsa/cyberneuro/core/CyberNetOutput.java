package com.igormaznitsa.cyberneuro.core;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class CyberNetOutput implements CyberNetEntity {
  private static final AtomicLong ID_GENERATOR = new AtomicLong();
  private final long id;

  CyberNetOutput(final long id) {
    this.id = id;
  }

  @Override
  public boolean isInputIndexValid(final int index) {
    return index == 0;
  }

  static CyberNetOutput makeNew() {
    return new CyberNetOutput(ID_GENERATOR.incrementAndGet());
  }

  @Override
  public boolean equals(final Object thatObj) {
    if (this == thatObj) {
      return true;
    }
    if (thatObj == null || getClass() != thatObj.getClass()) {
      return false;
    }
    final CyberNetOutput that = (CyberNetOutput) thatObj;
    return this.id == that.id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.id);
  }

}
