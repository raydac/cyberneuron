package com.igormaznitsa.cyberneuro.core;

@FunctionalInterface
public interface TripleConsumer<A, B, C> {
  void accept(A a, B b, C c);
}
