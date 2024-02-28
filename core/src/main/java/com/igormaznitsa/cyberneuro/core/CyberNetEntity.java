package com.igormaznitsa.cyberneuro.core;

public interface CyberNetEntity extends HasUid {

  default boolean isValidInternally() {
    return true;
  }

  CyberNetEntity makeCopy();
}
