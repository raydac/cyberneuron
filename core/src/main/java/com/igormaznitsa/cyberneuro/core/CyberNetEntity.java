package com.igormaznitsa.cyberneuro.core;

public interface CyberNetEntity extends HasInput {

  default boolean hasInternalErrors() {
    return false;
  }

  CyberNetEntity makeCopy();
}
