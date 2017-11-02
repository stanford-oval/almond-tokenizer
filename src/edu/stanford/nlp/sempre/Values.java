package edu.stanford.nlp.sempre;

import edu.stanford.nlp.sempre.thingtalk.LocationValue;
import edu.stanford.nlp.sempre.thingtalk.TypedStringValue;
import fig.basic.LispTree;

// FIXME: Remove this dependency

/**
 * Utilities for Value.
 *
 * @author Percy Liang
 */
public final class Values {
  private Values() { }

  // Try to parse the LispTree into a value.
  // If it fails, just return null.
  public static Value fromLispTreeOrNull(LispTree tree) {
    if (tree.isLeaf())
      return null;
    String type = tree.child(0).value;
    if ("boolean".equals(type)) return new BooleanValue(tree);
    if ("number".equals(type)) return new NumberValue(tree);
    if ("string".equals(type)) return new StringValue(tree);
    if ("date".equals(type)) return new DateValue(tree);
    if ("time".equals(type)) return new TimeValue(tree);
    if ("location".equals(type)) return new LocationValue(tree);
    if ("typedstring".equals(type)) return new TypedStringValue(tree);
    return null;
  }

  // Try to parse.  If it fails, throw an exception.
  public static Value fromLispTree(LispTree tree) {
    Value value = fromLispTreeOrNull(tree);
    if (value == null)
      throw new RuntimeException("Invalid value: " + tree);
    return value;
  }

  public static Value fromString(String s) { return fromLispTree(LispTree.proto.parseFromString(s)); }
}
