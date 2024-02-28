package com.igormaznitsa.cyberneuro.core;

import java.util.concurrent.atomic.AtomicLong;

public interface HasUid {
  AtomicLong UID_GENERATOR = new AtomicLong();

  long getUid();

}
