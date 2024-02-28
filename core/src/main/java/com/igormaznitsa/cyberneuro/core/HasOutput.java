package com.igormaznitsa.cyberneuro.core;

public interface HasOutput {
  int getOutputSize();

  boolean isOutputIndexValid(int index);
}
