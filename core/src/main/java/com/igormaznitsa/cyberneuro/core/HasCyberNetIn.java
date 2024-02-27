package com.igormaznitsa.cyberneuro.core;

public interface HasCyberNetIn extends CyberNetEntity {
  int getInputSize();

  boolean isInputIndexValid(int index);
}
