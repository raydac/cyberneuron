package com.igormaznitsa.cyberneuro.core;

/**
 * Element that can be the source of links on numbered output slots.
 *
 * <p>The default shape is a single slot at index {@code 0}.
 */
public interface HasOutput extends HasUid {

  /**
   * How many output slots this element exposes.
   *
   * @return slot count, at least {@code 1} for the default shape
   */
  default int getOutputSize() {
    return 1;
  }

  /**
   * Whether {@code index} names one of the output slots.
   *
   * @param index candidate slot, zero-based
   * @return {@code true} when {@code index} is inside {@code 0..outputSize-1}
   */
  default boolean isOutputIndexValid(int index) {
    return index == 0;
  }
}
