package com.igormaznitsa.cyberneuro.core;

public interface HasOutput extends HasUid {
  default int getOutputSize() {
    return 1;
  }

  default boolean isOutputIndexValid(int index) {
    return index == 0;
  }

}
