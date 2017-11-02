package edu.stanford.nlp.sempre;

import java.util.Comparator;
import java.util.Map;

import fig.basic.LogInfo;

/**
 * Values represent denotations (or partial denotations).
 *
 * @author Percy Liang
 */
public abstract class Value {
  public abstract Map<String, Object> toJson();
  @Override public abstract boolean equals(Object o);
  @Override public abstract int hashCode();
}
