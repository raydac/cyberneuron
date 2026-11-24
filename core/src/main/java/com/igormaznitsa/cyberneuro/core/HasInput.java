package com.igormaznitsa.cyberneuro.core;

/**
 * Element that can receive links on numbered input slots.
 *
 * <p>The default shape is a single slot at index {@code 0}.
 */
public interface HasInput extends HasUid {

  /**
   * How many input slots this element exposes.
   *
   * @return slot count, at least {@code 1} for the default shape
   */
  default int getInputSize() {
    return 1;
  }

  /**
   * Whether {@code index} names one of the input slots.
   *
   * @param index candidate slot, zero-based
   * @return {@code true} when {@code index} is inside {@code 0..inputSize-1}
   */
  default boolean isInputIndexValid(int index) {
    return index == 0;
  }
}
