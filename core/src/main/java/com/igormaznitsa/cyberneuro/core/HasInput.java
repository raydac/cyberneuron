package com.igormaznitsa.cyberneuro.core;

public interface HasInput extends HasUid {
  int getInputSize();

  boolean isInputIndexValid(int index);
}
