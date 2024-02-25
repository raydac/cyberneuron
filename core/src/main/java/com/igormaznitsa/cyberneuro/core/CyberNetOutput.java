package com.igormaznitsa.cyberneuro.core;

import java.util.Objects;

public class CyberNetOutput implements CyberNetEntity {
  private final long uid;

  CyberNetOutput(final long uid) {
    this.uid = uid;
  }

  static CyberNetOutput makeNew() {
    return new CyberNetOutput(UID_GENERATOR.incrementAndGet());
  }

  @Override
  public long getUid() {
    return this.uid;
  }

  @Override
  public boolean isInputIndexValid(final int index) {
    return index == 0;
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
    return this.uid == that.uid;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.uid);
  }

}
