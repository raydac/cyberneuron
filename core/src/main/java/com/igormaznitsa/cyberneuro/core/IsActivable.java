package com.igormaznitsa.cyberneuro.core;

/**
 * Element that turns a full input vector into an output vector.
 */
public interface IsActivable {

  /**
   * Evaluates this element on one sample.
   *
   * @param inputs one value per input slot, in slot order
   * @return one value per output slot, in slot order
   */
  int[] activate(int[] inputs);
}
