/**
 * Discrete cyberneuron from Polikarpov, Dergachev, Rumyantsev and Golubchikov.
 *
 * <p>A neuron keeps one substitution row per input. Activation sums the cells
 * those inputs address. {@link com.igormaznitsa.cyberneuro.core.CyberNet} wires
 * neurons into a graph. {@link com.igormaznitsa.cyberneuro.core.CyberTree} grows
 * joint tables and scores wirings with the paper's balanced recognition share.
 * Teaching rewrites addressed cells. It does not propagate a gradient.
 */

package com.igormaznitsa.cyberneuro.core;
