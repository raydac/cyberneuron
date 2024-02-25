package com.igormaznitsa.cyberneuro.core;

import java.util.Objects;

public final class CyberNetInput implements CyberNetEntity {

  private final long uid;

  CyberNetInput(final long uid) {
    this.uid = uid;
  }

  static CyberNetInput makeNew() {
    return new CyberNetInput(UID_GENERATOR.incrementAndGet());
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
    final CyberNetInput that = (CyberNetInput) thatObj;
    return this.uid == that.uid;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.uid);
  }
}
