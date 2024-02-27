package com.igormaznitsa.cyberneuro.core;

public interface CyberNetEntityOut extends CyberNetEntity {
  int getOutputSize();

  boolean isOutputIndexValid(int index);
}
