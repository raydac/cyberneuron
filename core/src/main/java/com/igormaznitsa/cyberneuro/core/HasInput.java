package com.igormaznitsa.cyberneuro.core;

public interface HasInput {
  int getInputSize();

  boolean isInputIndexValid(int index);
}
