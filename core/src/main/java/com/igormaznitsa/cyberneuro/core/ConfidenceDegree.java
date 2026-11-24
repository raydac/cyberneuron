package com.igormaznitsa.cyberneuro.core;

/**
 * Band of a neuron sum on the recognition scale.
 *
 * <p>Cuts sit on a signed-byte accumulator: {@link #NO} at or below 25,
 * {@link #MAY_BE_NO} from 26 through 63, {@link #MAY_BE_YES} from 64 through 102,
 * and {@link #YES} from 103 upward. {@link #ordinal()} is the symbol
 * {@link CyberNeuron#signal(int[])} publishes to a later table, so a downstream
 * neuron that consumes signals needs a max symbol of at least {@link #YES}'s ordinal.
 */
public enum ConfidenceDegree {

  /**
   * Sum is at most 25, so the pattern is unrecognized.
   */
  NO,

  /** Sum is from 26 through 63, a weak resemblance. */
  MAY_BE_NO,

  /** Sum is from 64 through 102, a partial match. */
  MAY_BE_YES,

  /** Sum is at least 103, so the pattern is recognized. */
  YES
}
