package com.igormaznitsa.cyberneuro.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Graph element that carries an id unique inside this JVM.
 */
public interface HasUid {

  /**
   * Counter behind ids assigned by factories in this package. It starts at zero
   * and only grows.
   */
  AtomicLong UID_GENERATOR = new AtomicLong();

  /**
   * Identity used for equality, copies, and diagram labels.
   *
   * @return id chosen at construction
   */
  long getUid();
}
