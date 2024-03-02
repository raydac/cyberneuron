package com.igormaznitsa.cyberneuro.core;

public interface CyberNetEntity extends HasUid {

  default boolean isInternallyValid() {
    return true;
  }

  CyberNetEntity makeCopy();
}
