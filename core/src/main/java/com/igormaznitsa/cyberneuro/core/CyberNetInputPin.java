package com.igormaznitsa.cyberneuro.core;

import java.util.Objects;

/**
 * Source pin of a {@link CyberNet}. Activation writes one sample symbol here,
 * and links carry that symbol onward. The pin has no input slots to wire.
 */
public final class CyberNetInputPin implements CyberNetEntity, HasOutput {

  private final long uid;

  CyberNetInputPin(final long uid) {
    this.uid = uid;
  }

  static CyberNetInputPin makeNew() {
    return new CyberNetInputPin(UID_GENERATOR.incrementAndGet());
  }

  @Override
  public CyberNetEntity makeCopy() {
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
