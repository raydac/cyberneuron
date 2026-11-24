/**
 * Discrete cyberneuron from Polikarpov, Dergachev, Rumyantsev and Golubchikov.
 *
 * <p>A neuron keeps one substitution row per input. Activation sums the cells
 * those inputs address. {@link com.igormaznitsa.cyberneuro.core.CyberNetwork}
 * wires neurons and teaches the stack by a signed gap that shrinks toward the
 * inputs.
 * Teaching rewrites addressed cells. It does not propagate a numeric derivative
 * through a table index.
 */

package com.igormaznitsa.cyberneuro.core;
