package edu.stanford.nlp.sempre;

/**
 * Values represent denotations (or partial denotations).
 *
 * @author Percy Liang
 */
public abstract class Value {
  @Override public abstract boolean equals(Object o);
  @Override public abstract int hashCode();
}
