package com.igormaznitsa.cyberneuro.core;

/**
 * Three-argument action used by {@link LearnStrategy} to rewrite one neuron.
 *
 * @param <A> first argument, the neuron being taught
 * @param <B> second argument, the input vector
 * @param <C> third argument, the signed step count
 */
@FunctionalInterface
public interface TripleConsumer<A, B, C> {

  /**
   * Performs the action on one triple.
   *
   * @param first  neuron, input vector, or other first value
   * @param second value paired with {@code first}
   * @param third  value paired with {@code first} and {@code second}
   */
  void accept(A first, B second, C third);
}
