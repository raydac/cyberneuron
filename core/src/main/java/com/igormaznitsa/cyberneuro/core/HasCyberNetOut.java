package com.igormaznitsa.cyberneuro.core;

public interface HasCyberNetOut extends CyberNetEntity {
  int getOutputSize();

  boolean isOutputIndexValid(int index);
}
