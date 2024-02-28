package com.igormaznitsa.cyberneuro.core;

import java.util.concurrent.atomic.AtomicLong;

public interface CyberNetEntity {

  AtomicLong UID_GENERATOR = new AtomicLong();

  long getUid();

  default boolean isValidInternally() {
    return true;
  }
}
