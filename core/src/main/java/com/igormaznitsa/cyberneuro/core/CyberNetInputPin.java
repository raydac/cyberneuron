package com.igormaznitsa.cyberneuro.core;

import java.util.Objects;

public final class CyberNetInputPin implements CyberNetEntity, HasOutput {

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
  public int getOutputSize() {
    return 1;
  }

  @Override
  public boolean isOutputIndexValid(final int index) {
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
    final CyberNetInputPin that = (CyberNetInputPin) thatObj;
    return this.uid == that.uid;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.uid);
  }
}
