package com.igormaznitsa.cyberneuro.core;

/**
 * Member that can be placed in a {@link CyberNet}.
 */
public interface CyberNetEntity extends HasInput {

  /**
   * Whether this member alone is structurally invalid.
   *
   * @return {@code false} unless a subtype finds a broken internal invariant
   */
  default boolean hasInternalErrors() {
    return false;
  }

  /**
   * Separate member with the same behavior and a new {@link HasUid#getUid()}.
   *
   * @return the copy; the original is left unchanged
   */
  CyberNetEntity makeCopy();
}
