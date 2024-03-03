package com.igormaznitsa.cyberneuro.core;

public interface HasInput extends HasUid {
  default int getInputSize() {
    return 1;
  }

  default boolean isInputIndexValid(int index) {
    return index == 0;
  }
}
