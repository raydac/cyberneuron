package com.igormaznitsa.cyberneuro.core;

/**
 * Element whose mutating operations can be frozen.
 */
public interface HasLock {

  /**
   * Whether mutating operations must refuse to run.
   *
   * @return {@code true} after {@link #setLock(boolean)} has been called with {@code true}
   */
  boolean isLocked();

  /**
   * Freezes or releases mutating operations.
   *
   * @param flag {@code true} to reject later mutations, {@code false} to allow them
   */
  void setLock(boolean flag);

  /**
   * Rejects the caller when this element is frozen.
   *
   * @throws IllegalStateException when {@link #isLocked()} is {@code true}
   */
  default void assertNonLocked() {
    if (this.isLocked()) {
      throw new IllegalStateException("Is locked");
    }
  }
}
