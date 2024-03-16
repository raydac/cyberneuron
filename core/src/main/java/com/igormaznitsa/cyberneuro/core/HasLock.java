package com.igormaznitsa.cyberneuro.core;

public interface HasLock {
  boolean isLocked();

  void setLock(boolean flag);

  default void assertNonLocked() {
    if (this.isLocked()) {
      throw new IllegalStateException("Is locked");
    }
  }
}
