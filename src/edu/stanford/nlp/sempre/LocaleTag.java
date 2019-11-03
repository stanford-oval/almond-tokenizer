package edu.stanford.nlp.sempre;

import java.util.*;
import com.google.common.base.Joiner;

public class LocaleTag {
  private final String tag;
  private final String language;
  private final String script;
  private final String region;
  private final String[] parts;

  private static final Map<String, String> SCRIPTS = new HashMap<>();
  static {
    SCRIPTS.put("zh-cn", "hans");
    SCRIPTS.put("zh-tw", "hant");
  }

  public LocaleTag(List<String> split) {
    String language = split.get(0);

    String script;
    if (split.size() > 1 && split.get(1).length() == 4)
      script = split.get(1);
    else
      script = null;

    String region;
    if (script != null && split.size() > 2)
      region = split.get(2);
    else if (script == null && split.size() > 1)
      region = split.get(1);
    else
      region = null;

    int otherOffset = 1;
    if (script != null)
      otherOffset++;
    if (region != null)
      otherOffset++;

    String[] other = new String[split.size() - otherOffset];
    for (int i = otherOffset; i < split.size(); i++)
      other[i - otherOffset] = split.get(i);

    if (region != null && script == null) {
      // apply region-based fallbacks for script
      if (SCRIPTS.containsKey(language + "-" + region))
        script = SCRIPTS.get(language + "-" + region);
    }

    this.language = language;
    this.script = script;
    this.region = region;

    String normalizedTag = this.language;
    int partCount = 1 + (script != null ? 1 : 0) + (region != null ? 1 : 0) + other.length;
    String[] parts = new String[partCount];

    int i = 0;
    parts[i++] = this.language;
    if (this.script != null)
      parts[i++] = this.script;
    if (this.region != null)
      parts[i++] = this.region;
    System.arraycopy(other, 0, parts, i, other.length);

    this.parts = parts;
    this.tag = Joiner.on("-").join(parts);
  }

  public LocaleTag(String tag) {
    this(Arrays.asList(tag.toLowerCase().split("-")));
  }

  public String toString() {
    return this.tag;
  }
  public boolean equals(Object other) {
    if (other == null || !(other instanceof LocaleTag))
      return false;
    return this.tag.equals(((LocaleTag)other).tag);
  }
  public int hashCode() {
    return this.tag.hashCode();
  }

  public String getLanguage() {
    return this.language;
  }
  public String getScript() {
    return this.script;
  }
  public String getRegion() {
    return this.region;
  }

  public LocaleTag[] getFallbacks() {
    List<String> partList = Arrays.asList(parts);
    LocaleTag[] fallbacks = new LocaleTag[partList.size()];

    for (int i = 0; i < fallbacks.length; i++)
      fallbacks[i] = new LocaleTag(partList.subList(0, partList.size()-i));
    return fallbacks;
  }
}
