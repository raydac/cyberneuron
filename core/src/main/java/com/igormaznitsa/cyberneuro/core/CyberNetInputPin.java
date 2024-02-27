package com.igormaznitsa.cyberneuro.core;

import java.util.Objects;

public final class CyberNetInputPin implements CyberNetEntityIn {

  private final long uid;

  CyberNetInputPin(final long uid) {
    this.uid = uid;
  }

  static CyberNetInputPin makeNew() {
    return new CyberNetInputPin(UID_GENERATOR.incrementAndGet());
  }

  @Override
  public long getUid() {
    return this.uid;
  }

  @Override
  public int getInputSize() {
    return 1;
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
    final CyberNetInputPin that = (CyberNetInputPin) thatObj;
    return this.uid == that.uid;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.uid);
  }
}
