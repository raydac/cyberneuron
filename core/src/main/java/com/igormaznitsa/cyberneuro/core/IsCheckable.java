package com.igormaznitsa.cyberneuro.core;

import java.util.List;

public interface IsCheckable {
  List<ConfidenceDegree> check(int[] inputs);

}
