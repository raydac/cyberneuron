package com.igormaznitsa.cyberneuro.core;

import java.util.Objects;

/**
 * Sink pin of a {@link CyberNet}. The pin is a {@link IsTerminator}, and
 * {@link #activate(int[])} forwards its single incoming value unchanged.
 */
public class CyberNetOutputPin implements CyberNetEntity, IsActivable, HasOutput, IsTerminator {
  private final long uid;

  CyberNetOutputPin(final long uid) {
    this.uid = uid;
  }

  static CyberNetOutputPin makeNew() {
    return new CyberNetOutputPin(UID_GENERATOR.incrementAndGet());
  }

  @Override
  public CyberNetEntity makeCopy() {
    return new CyberNetOutputPin(UID_GENERATOR.incrementAndGet());
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
    final CyberNetOutputPin that = (CyberNetOutputPin) thatObj;
    return this.uid == that.uid;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.uid);
  }

  /**
   * Returns the incoming value as the pin output.
   *
   * @param inputs one-element vector delivered by the single incoming link
   * @return the same array instance
   */
  @Override
  public int[] activate(int[] inputs) {
    return inputs;
  }

}
