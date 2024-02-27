package com.igormaznitsa.cyberneuro.core;

public interface CyberNetEntityIn extends CyberNetEntity {
  int getInputSize();

  boolean isInputIndexValid(int index);
}
