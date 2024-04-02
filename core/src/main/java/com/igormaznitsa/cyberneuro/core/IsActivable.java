package com.igormaznitsa.cyberneuro.core;

import java.util.List;

public interface IsActivable {
  List<ConfidenceDegree> activate(int[] inputs);

}
