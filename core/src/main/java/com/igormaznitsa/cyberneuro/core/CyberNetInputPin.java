package com.igormaznitsa.cyberneuro.core;

import java.util.Objects;

public final class CyberNetInputPin implements CyberNetEntity, HasSingleOutput {

  private final long uid;

  CyberNetInputPin(final long uid) {
    this.uid = uid;
  }

  @Override
  public CyberNetEntity makeCopy() {
    return new CyberNetInputPin(UID_GENERATOR.incrementAndGet());
  }

  static CyberNetInputPin makeNew() {
    return new CyberNetInputPin(UID_GENERATOR.incrementAndGet());
  }

  @Override
  public long getUid() {
    return this.uid;
  }

  @Override
  public boolean equals(final Object thatObj) {
    if (this == thatObj) {
      return true;
    }
    if (thatObj == null || getClass() != thatObj.getClass()) {
      return false;
    }
    final CyberNetInputPin that = (CyberNetInputPin) thatObj;
    return this.uid == that.uid;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.uid);
  }
}
