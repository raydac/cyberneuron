package com.igormaznitsa.cyberneuro.core;

public interface HasOutput extends HasUid {
  int getOutputSize();

  boolean isOutputIndexValid(int index);
}
